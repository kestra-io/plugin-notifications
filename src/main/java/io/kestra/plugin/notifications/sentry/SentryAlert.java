package io.kestra.plugin.notifications.sentry;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static io.kestra.core.utils.Rethrow.throwSupplier;
import static java.nio.charset.StandardCharsets.UTF_8;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Sentry alert when a specific flow or task fails.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. \n\n The only required input is a DSN string value, which you can find when you go to your Sentry project settings and go to the section `Client Keys (DSN)`. You can find more detailed description of how to find your DSN in the [following Sentry documentation](https://docs.sentry.io/product/sentry-basics/concepts/dsn-explainer/#where-to-find-your-dsn). \n\n You can customize the alert `payload`, which is a JSON object, or you can skip it and use the default payload created by Kestra. For more information about the payload, check the [Sentry Event Payloads documentation](https://develop.sentry.dev/sdk/event-payloads/). \n\n The `event_id` is an optional payload attribute that you can use to override the default event ID. If you don't specify it (recommended), Kestra will generate a random UUID. You can use this attribute to group events together, but note that this must be a UUID type. For more information, check the [Sentry documentation](https://docs.sentry.io/product/issues/grouping-and-fingerprints/)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Sentry alert on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}" # format: https://xxx@xxx.ingest.sentry.io/xxx
                    endpointType: ENVELOPE"""
        ),
        @Example(
            title = "Send a custom Sentry alert",
            full = true,
            code = """
                id: sentry_alert
                namespace: company.team

                tasks:
                  - id: send_sentry_message
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}"
                    endpointType: "ENVELOPE"
                    payload: |
                      {
                          "timestamp": "{{ execution.startDate }}",
                          "platform": "java",
                          "level": "error",
                          "transaction": "/execution/id/{{ execution.id }}",
                          "server_name": "localhost:8080",
                          "message": {
                            "message": "Execution {{ execution.id }} failed"
                          },
                          "extra": {
                            "Namespace": "{{ flow.namespace }}",
                            "Flow ID": "{{ flow.id }}",
                            "Execution ID": "{{ execution.id }}",
                            "Link": "http://localhost:8080/ui/executions/{{flow.namespace}}/{{flow.id}}/{{execution.id}}"
                          }
                      }"""
        ),
    }
)
public class SentryAlert extends AbstractHttpOptionsTask {
    public static final String SENTRY_VERSION = "7";
    public static final String SENTRY_CLIENT = "java";
    public static final String SENTRY_DATA_MODEL = "event";
    public static final String SENTRY_FILE_NAME = "application.log";
    public static final String SENTRY_CONTENT_TYPE = "application/json";
    public static final String SENTRY_DSN_REGEXP = "^(https?://[a-f0-9]+@o[0-9]+\\.ingest\\.sentry\\.io/[0-9]+)$";
    public static final int PAYLOAD_SIZE_THRESHOLD = 1024 * 1024;    // 1MB for events
    public static final int ENVELOP_SIZE_THRESHOLD = 100 * 1024 * 1024;  // 100MB decompressed

    private static final String DEFAULT_PAYLOAD = """
        {
          "timestamp": "{{ execution.startDate }}",
          "platform": "java",
          "level": "error",
          "message": {
            "message": "Execution {{ execution.id }} failed"
          },
          "extra": {
            "Namespace": "{{ flow.namespace }}",
            "Flow ID": "{{ flow.id }}",
            "Execution ID": "{{ execution.id }}"
          }
        }""";

    @Schema(
        title = "Sentry DSN"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String dsn;

    @Schema(
        title = "Sentry endpoint type"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    protected EndpointType endpointType = EndpointType.ENVELOPE;

    @Schema(
        title = "Sentry event payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String dsn = runContext.render(this.dsn);

        String url = dsn;
        if (dsn.matches(SENTRY_DSN_REGEXP)) {
            /*
            To make passing the correct API endpoint URL easier,
            users only need to provide the Sentry DSN, and we parse the required attributes for the URL
            using the following formats:
            STORE_URL: https://{HOST}/api/{PROJECT_ID}/store/?sentry_version=7&sentry_client=java&sentry_key={PUBLIC_KEY}
            ENVELOPE_URL: https://{HOST}/api/{PROJECT_ID}/envelope/?sentry_version=7&sentry_client=java&sentry_key={PUBLIC_KEY}
            */
            url = switch (endpointType) {
                case ENVELOPE -> EndpointType.ENVELOPE.getEnvelopeUrl(dsn);
                case STORE -> EndpointType.STORE.getEnvelopeUrl(dsn);
            };
        }

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(this.payload).as(String.class)
                .orElseGet(throwSupplier(() -> runContext.render(DEFAULT_PAYLOAD.strip())));

            // Constructing the envelope payload
            String envelope = constructEnvelope((String) runContext.getVariables().get("eventId"), payload);

            // Trying to send to /envelope endpoint
            try {
                runContext.logger().debug("Attempting to send the following Sentry event envelope: {}", envelope);
                HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                    .addHeader("Content-Type", "application/json")
                    .uri(URI.create(url))
                    .method("POST")
                    .body(io.kestra.core.http.HttpRequest.StringRequestBody.builder()
                        .content(envelope)
                        .build());

                HttpRequest request = requestBuilder.build();

                HttpResponse<String> response = client.request(request, String.class);

                runContext.logger().debug("Response: {}", response.getBody());

                if (response.getStatus().getCode() == 200) {
                    runContext.logger().info("Request succeeded");
                }
            } catch (HttpClientResponseException exception) { // Backward Compatibility cases
                int errorCode = Objects.requireNonNull(exception.getResponse()).getStatus().getCode();
                if ((errorCode == 401 || errorCode == 404) && endpointType.equals(EndpointType.ENVELOPE)) {
                    // If the /envelope endpoint is Not Found or Unauthorized ("missing authorization information"), request UI to configure endpointType: store to send the request to /store endpoint.
                    runContext.logger().error("Envelope endpoint not supported; Please try to configure the store endpoint instead: endpointType: store");
                    throw exception;
                }
            }
        }

        return null;
    }

    /**
     * Helper method to construct the Envelope formatted payload.
     */
    private String constructEnvelope(String eventId, String payload) {
        return switch (endpointType) {
            case ENVELOPE -> {
                // Build Envelope Payload
                String envelope = "%s%n%s%n%s%n".formatted(getEnvelopeHeaders(eventId, dsn), getItemHeaders(payload.length()), payload);

                // Check envelope and payload against threshold sizes
                checkEnvelopeAndPayloadThresholds(envelope, payload);

                yield envelope;
            }
            case STORE -> payload;
        };
    }

    /**
     * Helper method to build envelope headers
     */
    private static String getEnvelopeHeaders(String eventId, String dsn) {
        eventId = Objects.isNull(eventId) ? UUID.randomUUID().toString().toLowerCase().replace("-", "") : eventId;
        String sentAt = Instant.now().toString();
        return "{\"event_id\":\"%s\",\"dsn\":\"%s\",\"sdk\":{\"name\":\"%s\",\"version\":\"%s\"},\"sent_at\":\"%s\"}".formatted(eventId, dsn, SENTRY_CLIENT, SENTRY_VERSION, sentAt);
    }

    /**
     * Helper method to build item headers
     */
    private static String getItemHeaders(int payloadLength) {
        return "{\"type\":\"%s\",\"length\":%d,\"content_type\":\"%s\",\"filename\":\"%s\"}".formatted(SENTRY_DATA_MODEL, payloadLength, SENTRY_CONTENT_TYPE, SENTRY_FILE_NAME);
    }

    /**
     * Helper method to Check envelope and payload against threshold sizes.
     */
    private static void checkEnvelopeAndPayloadThresholds(String envelope, String payload) {
        // Calculate the size of the envelope
        int envelopeSize = envelope.getBytes(UTF_8).length;
        int payloadSize = payload.getBytes(UTF_8).length;

        // Enforce size limits based on Sentry's documentation
        if (envelopeSize > ENVELOP_SIZE_THRESHOLD) {
            throw new IllegalArgumentException("Envelope size exceeds 100MB limit for decompressed data");
        }

        if (payloadSize > PAYLOAD_SIZE_THRESHOLD) {
            throw new IllegalArgumentException("Event payload size exceeds 1MB limit");
        }
    }
}
