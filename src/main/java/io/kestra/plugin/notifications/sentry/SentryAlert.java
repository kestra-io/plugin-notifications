package io.kestra.plugin.notifications.sentry;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Sentry alert",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. " +
        "Check the <a href=\"https://develop.sentry.dev/sdk/event-payloads/\">Sentry events documentation</a> " +
        "Check the <a href=\"https://docs.sentry.io/product/sentry-basics/concepts/dsn-explainer/#where-to-find-your-dsn\">Sentry where to find your dsn documentation</a> " +
        "for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Sentry alert on a failed flow execution",
            full = true,
            code = """
                id: unreliable_flow
                namespace: prod

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}" # format: https://x11xx11x1xxx11x11x11x11111x11111@o11111.ingest.sentry.io/1
                    payload: |
                      {
                          "event_id": "fc6d8c0c43fc4630ad850ee518f1b9d1",
                          "timestamp": "{{ execution.startDate }}",
                          "platform": "java",
                          "level": "error",
                          "transaction": "/execution/id/{{ execution.id }}",
                          "server_name": "localhost:8080",
                          "extra": {
                            "Namespace": "{{ flow.namespace }}",
                            "Flow ID": "{{ flow.id }}",
                            "Execution ID": "{{ execution.id }}",
                            "Execution Status": "{{ execution.state.current }}",
                            "Link": "{{link}}"
                          }
                      }
                """
        ),
        @Example(
            title = "Send a Sentry alert",
            full = true,
            code = """
                id: sentry_alert
                namespace: dev

                tasks:
                  - id: send_sentry_message
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}" # format: https://{PUBLIC_KEY}@{HOST}/{PROJECT_ID}
                    payload: |
                      {
                          "event_id": "fc6d8c0c43fc4630ad850ee518f1b9d0",
                          "timestamp": "{{ execution.startDate }}",
                          "platform": "java",
                          "level": "info",
                          "transaction": "/execution/id/{{ execution.id }}",
                          "server_name": "localhost:8080",
                          "extra": {
                            "Namespace": "{{ flow.namespace }}",
                            "Flow ID": "{{ flow.id }}",
                            "Execution ID": "{{ execution.id }}",
                            "Execution Status": "{{ execution.state.current }}",
                            "Link": "{{link}}"
                          }
                      }
                """
        ),
    }
)
public class SentryAlert extends Task implements RunnableTask<VoidOutput> {

    public static final String SENTRY_VERSION = "7";
    public static final String SENTRY_CLIENT = "java";
    public static final String SENTRY_DSN_REGEXP = "^(https?://[a-f0-9]+@o[0-9]+\\.ingest\\.sentry\\.io/[0-9]+)$";

    @Schema(
        title = "Sentry DSN"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String dsn;

    @Schema(
        title = "Sentry event payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String dsn = runContext.render(this.dsn);

        String url = dsn;
        if (dsn.matches(SENTRY_DSN_REGEXP)) {
            String protocol = dsn.split("://")[0];
            String publicKey = dsn.split("@")[0].replace(protocol + "://", "");
            String host = dsn.split("@")[1].split("/")[0];
            String projectId = dsn.split("@")[1].split("/")[1];

            url = "%s://%s/api/%s/store/?sentry_version=%s&sentry_client=%s&sentry_key=%s" // https://{HOST}/api/{PROJECT_ID}/store/?sentry_version=7&sentry_client=java&sentry_key={PUBLIC_KEY}
                .formatted(protocol, host, projectId, SENTRY_VERSION, SENTRY_CLIENT, publicKey);
        }

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            if (this.payload == null) {
                setDefaultPayload();
            }
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Sentry event: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }

    private void setDefaultPayload() throws JsonProcessingException {
        Map<String, Object> map = new HashMap<>();

        map.put("event_id", UUID.randomUUID().toString().toLowerCase().replace("-", ""));
        map.put("timestamp", Instant.now().toString());
        map.put("platform", Platform.JAVA.name().toLowerCase());
        map.put("level", ErrorLevel.INFO.name().toLowerCase());

        map.put("exception", Map.of("values", List.of(Map.of("type", "Kestra Alert"))));

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);
    }
}
