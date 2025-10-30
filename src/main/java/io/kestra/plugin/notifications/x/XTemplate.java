package io.kestra.plugin.notifications.x;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString(exclude = {"bearerToken", "consumerKey", "consumerSecret", "accessToken", "accessSecret"})
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class XTemplate extends AbstractHttpOptionsTask {

    private static final String OAUTH_1_ALGORITHM = "HmacSHA1";
    private static final int MAX_POST_LENGTH = 280;

    @Schema(title = "Bearer Token", description = "X API Bearer Token for authentication. If provided, OAuth 1.0a credentials are not required.")
    protected Property<String> bearerToken;

    @Schema(title = "OAuth Consumer Key", description = "X API OAuth 1.0a Consumer Key (API Key). Required if bearerToken is not provided.")
    protected Property<String> consumerKey;

    @Schema(title = "OAuth Consumer Secret", description = "X API OAuth 1.0a Consumer Secret (API Secret). Required if bearerToken is not provided.")
    protected Property<String> consumerSecret;

    @Schema(title = "OAuth Access Token", description = "X API OAuth 1.0a Access Token. Required if bearerToken is not provided.")
    protected Property<String> accessToken;

    @Schema(title = "OAuth Access Secret", description = "X API OAuth 1.0a Access Token Secret. Required if bearerToken is not provided.")
    protected Property<String> accessSecret;

    @Schema(title = "Template to use", hidden = true)
    protected Property<String> templateUri;

    @Schema(title = "Map of variables to use for the message template")
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Post text body", description = "Direct post text (bypasses template)")
    protected Property<String> textBody;

    @Schema(title = "Override URL for testing", description = "Optional URL to override the default X API endpoint (for testing purposes)")
    @Builder.Default
    protected Property<String> url = Property.ofValue("https://api.x.com/2/tweets");

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final var rUrl = runContext.render(this.url).as(String.class).orElse("https://api.x.com/2/tweets");

        String authHeader;
        final var rBearerToken = runContext.render(this.bearerToken).as(String.class);

        if (rBearerToken.isPresent()) {
            authHeader = "Bearer " + rBearerToken.orElseThrow();
        } else {
            String rConsumerKeyValue = runContext.render(this.consumerKey).as(String.class).orElseThrow();
            String rConsumerSecretValue = runContext.render(this.consumerSecret).as(String.class).orElseThrow();
            String rAccessTokenValue = runContext.render(this.accessToken).as(String.class).orElseThrow();
            String rAccessSecretValue = runContext.render(this.accessSecret).as(String.class).orElseThrow();

            authHeader = buildOAuth1Header(
                runContext,
                rUrl,
                rConsumerKeyValue,
                rConsumerSecretValue,
                rAccessTokenValue,
                rAccessSecretValue);
        }

        String rPostText = getPostText(runContext);

        if (rPostText.length() > MAX_POST_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Tweet message exceeds maximum length of %d characters. Current length: %d",
                    MAX_POST_LENGTH, rPostText.length()));
        }

        Map<String, Object> postPayload = new HashMap<>();
        postPayload.put("text", rPostText);

        String payload = JacksonMapper.ofJson().writeValueAsString(postPayload);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            runContext.logger().debug("Sending X post");

            HttpRequest request = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .uri(URI.create(rUrl))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder().content(payload).build())
                .build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() == 201) {
                runContext.logger().info("X post sent successfully");
            } else {
                runContext.logger().error("Failed to send X post: {}", response.getBody());
            }
        }

        return null;
    }

    private String getPostText(RunContext runContext) throws Exception {
        final var rTextBody = runContext.render(this.textBody).as(String.class);
        final var rTemplateUri = runContext.render(this.templateUri).as(String.class);

        if (rTemplateUri.isPresent()) {
            var resourceStream = this.getClass().getClassLoader().getResourceAsStream(rTemplateUri.get());
            if (resourceStream == null) {
                throw new IllegalArgumentException("Template resource not found: " + rTemplateUri.get());
            }
            String template = IOUtils.toString(
                resourceStream,
                StandardCharsets.UTF_8);

            Map<String, Object> rTemplateVars = runContext.render(templateRenderMap).asMap(String.class, Object.class);

            return runContext.render(template, rTemplateVars);
        }

        return rTextBody.orElse("");
    }

    private String buildOAuth1Header(RunContext runContext, String url,
                                     String consumerKey, String consumerSecret, String token, String secret) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = UUID.randomUUID().toString().replace("-", "");

            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", consumerKey);
            oauthParams.put("oauth_nonce", nonce);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", timestamp);
            oauthParams.put("oauth_token", token);
            oauthParams.put("oauth_version", "1.0");

            String signatureBaseString = createSignatureBaseString(url, oauthParams);

            String signingKey = URLEncoder.encode(consumerSecret, StandardCharsets.UTF_8) + "&" +
                URLEncoder.encode(secret, StandardCharsets.UTF_8);

            String signature = generateSignature(signatureBaseString, signingKey);

            return String.format(
                "OAuth oauth_consumer_key=\"%s\", oauth_nonce=\"%s\", oauth_signature=\"%s\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"%s\", oauth_token=\"%s\", oauth_version=\"1.0\"",
                consumerKey,
                nonce,
                URLEncoder.encode(signature, StandardCharsets.UTF_8),
                timestamp,
                token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OAuth 1.0a header", e);
        }
    }

    private String createSignatureBaseString(String url, Map<String, String> params) {
        String sortedParams = params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

        return "POST" + "&" +
            URLEncoder.encode(url, StandardCharsets.UTF_8) + "&" +
            URLEncoder.encode(sortedParams, StandardCharsets.UTF_8);
    }

    private String generateSignature(String signatureBaseString, String signingKey)
        throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(OAUTH_1_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), OAUTH_1_ALGORITHM);
        mac.init(secretKeySpec);

        byte[] signature = mac.doFinal(signatureBaseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }
}
