package io.kestra.plugin.notifications.x;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a tweet/post to X (formerly Twitter) using API.",
    description = "Add this task to send direct X notifications. Check the <a href=\"https://docs.x.com/x-api/introduction\">X API documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a tweet on a failed flow execution.",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.notifications.x.XIncomingWebhook
                    bearerToken: "{{ secret('X_API_BEARER_TOKEN') }}"
                    message: "⚠️ Flow '{{ flow.namespace }}.{{ flow.id }}' failed! #Kestra #Alert"
                """
        ),
        @Example(
            title = "Send a success notification with custom message.",
            full = true,
            code = """
                id: data_pipeline
                namespace: company.team

                tasks:
                  - id: process_data
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - echo "Data processing completed successfully"

                  - id: notify_success
                    type: io.kestra.plugin.notifications.x.XIncomingWebhook
                    bearerToken: "{{ secret('X_API_BEARER_TOKEN') }}"
                    message: "✅ Data pipeline completed successfully! #DataOps #Kestra"
                """
        )
    }
)
public class XIncomingWebhook extends AbstractHttpOptionsTask {

    private static final int MAX_TWEET_LENGTH = 280;
    private static final String OAUTH_1_ALGORITHM = "HmacSHA1";

    @Schema(
        title = "X API endpoint URL",
        description = "The X API endpoint URL for posting tweets. Defaults to https://api.x.com/2/tweets"
    )
    @PluginProperty(dynamic = true)
    protected Property<String> url;

    @Schema(
        title = "Bearer token for X API authentication",
        description = "OAuth 2.0 Bearer token for X API authentication. Either this or oauthToken/oauthSecret pair must be provided."
    )
    @PluginProperty
    protected Property<String> bearerToken;

    @Schema(
        title = "OAuth 1.0a consumer key for X API authentication",
        description = "OAuth 1.0a consumer key. Must be used together with consumerSecret, oauthToken, and oauthSecret."
    )
    @PluginProperty
    protected Property<String> consumerKey;

    @Schema(
        title = "OAuth 1.0a consumer secret for X API authentication",
        description = "OAuth 1.0a consumer secret. Must be used together with consumerKey, oauthToken, and oauthSecret."
    )
    @PluginProperty
    protected Property<String> consumerSecret;

    @Schema(
        title = "OAuth 1.0a token for X API authentication",
        description = "OAuth 1.0a access token. Must be used together with oauthSecret, consumerKey, and consumerSecret."
    )
    @PluginProperty
    protected Property<String> oauthToken;

    @Schema(
        title = "OAuth 1.0a secret for X API authentication",
        description = "OAuth 1.0a access token secret. Must be used together with oauthToken, consumerKey, and consumerSecret."
    )
    @PluginProperty
    protected Property<String> oauthSecret;

    @Schema(
        title = "Tweet content",
        description = "The text content of the tweet/post to be sent to X."
    )
    @PluginProperty(dynamic = true)
    protected Property<String> message;

    @Schema(
        title = "Media URLs",
        description = "List of public URLs to media files to attach to the tweet."
    )
    @PluginProperty
    protected Property<List<String>> mediaUrls;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String tweetMessage = runContext.render(this.message).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Message is required"));

        validateTweetLength(tweetMessage);

        Map<String, Object> tweetData = Map.of("text", tweetMessage);

        if (mediaUrls != null) {
            var mediaItems = runContext.render(mediaUrls).asList(String.class);
            if (!mediaItems.isEmpty()) {
                tweetData = Map.of(
                    "text", tweetMessage,
                    "media", Map.of("media_ids", mediaItems)
                );
            }
        }

        postTweet(runContext, tweetData);

        return null;
    }

    private void validateTweetLength(String message) {
        if (message.length() > MAX_TWEET_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Tweet message exceeds maximum length of %d characters. Current length: %d",
                    MAX_TWEET_LENGTH, message.length())
            );
        }
    }

    private void postTweet(RunContext runContext, Map<String, Object> tweetData) throws Exception {
        String endpointUrl = runContext.render(this.url).as(String.class)
            .orElse("https://api.x.com/2/tweets");

        // Prepare authentication header
        String authHeader = null;
        String bearerTokenValue = runContext.render(bearerToken).as(String.class).orElse(null);
        if (bearerTokenValue != null) {
            authHeader = "Bearer " + bearerTokenValue;
        } else {
            // OAuth 1.0a implementation
            String token = runContext.render(oauthToken).as(String.class).orElse(null);
            String secret = runContext.render(oauthSecret).as(String.class).orElse(null);
            String consumerKeyValue = runContext.render(consumerKey).as(String.class).orElse(null);
            String consumerSecretValue = runContext.render(consumerSecret).as(String.class).orElse(null);

            if (token != null && secret != null && consumerKeyValue != null && consumerSecretValue != null) {
                authHeader = buildOAuth1Header(runContext, endpointUrl, tweetData, consumerKeyValue, consumerSecretValue, token, secret);
            } else {
                throw new IllegalArgumentException("Either bearerToken or oauthToken/oauthSecret must be provided");
            }
        }

        // Send HTTP request
        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .uri(URI.create(endpointUrl))
                .method("POST")
                .body(HttpRequest.JsonRequestBody.builder()
                    .content(tweetData)
                    .build());

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() >= 200 && response.getStatus().getCode() < 300) {
                runContext.logger().info("Tweet posted successfully");
            } else {
                throw new RuntimeException("Failed to post tweet: " + response.getStatus().getCode() + " - " + response.getBody());
            }
        }
    }

    private String buildOAuth1Header(RunContext runContext, String url, Map<String, Object> data, String consumerKey, String consumerSecret, String token, String secret) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = UUID.randomUUID().toString().replace("-", "");

            // OAuth 1.0a parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", consumerKey);
            oauthParams.put("oauth_nonce", nonce);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", timestamp);
            oauthParams.put("oauth_token", token);
            oauthParams.put("oauth_version", "1.0");

            // Add request parameters (for signature base string)
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            if (data != null) {
                data.forEach((key, value) -> allParams.put(key, value.toString()));
            }

            // Create signature base string
            String signatureBaseString = createSignatureBaseString("POST", url, allParams);

            // Create signing key
            String signingKey = URLEncoder.encode(consumerSecret, StandardCharsets.UTF_8) + "&" + URLEncoder.encode(secret, StandardCharsets.UTF_8);

            // Generate signature
            String signature = generateSignature(signatureBaseString, signingKey);

            // Build OAuth header
            return String.format(
                "OAuth oauth_consumer_key=\"%s\", oauth_nonce=\"%s\", oauth_signature=\"%s\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"%s\", oauth_token=\"%s\", oauth_version=\"1.0\"",
                consumerKey,
                nonce,
                URLEncoder.encode(signature, StandardCharsets.UTF_8),
                timestamp,
                token
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OAuth 1.0a header", e);
        }
    }

    private String createSignatureBaseString(String method, String url, Map<String, String> params) {
        String sortedParams = params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                          URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

        return method + "&" +
               URLEncoder.encode(url, StandardCharsets.UTF_8) + "&" +
               URLEncoder.encode(sortedParams, StandardCharsets.UTF_8);
    }

    private String generateSignature(String signatureBaseString, String signingKey) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(OAUTH_1_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), OAUTH_1_ALGORITHM);
        mac.init(secretKeySpec);

        byte[] signature = mac.doFinal(signatureBaseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }
}
