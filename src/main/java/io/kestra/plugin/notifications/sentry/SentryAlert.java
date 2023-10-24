package io.kestra.plugin.notifications.sentry;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpHeaders;
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
                    url: "{{ secret('SENTRY_ALERT') }}" # format: https://{{HOST/URI}}/api/{{PROJECT_ID}}/store/?sentry_version=7&sentry_client=java&sentry_key={{PUBLIC_KEY}}
                    payload: |
                      {
                          "event_id": "fc6d8c0c43fc4630ad850ee518f1b9d1",
                          "timestamp": "2023-05-02T17:41:36Z",
                          "platform": "java",
                          "level": "error",
                          "transaction": "/execution/id/321312",
                          "server_name": "localhost:8080",
                          "extra": {
                            "Namespace": "{{execution.namespace}}",
                            "Flow ID": "{{execution.flowId}}",
                            "Execution ID": "{{execution.id}}",
                            "Execution Status": "{{execution.state.current}}",
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
                    url: "{{ secret('SENTRY_ALERT') }}" # format: https://{HOST/URI}/api/{PROJECT_ID}/store/?sentry_version=7&sentry_client=java&sentry_key={PUBLIC_KEY}
                    payload: |
                      {
                          "event_id": "fc6d8c0c43fc4630ad850ee518f1b9d0",
                          "timestamp": "2023-05-02T17:41:36Z",
                          "platform": "java",
                          "level": "info",
                          "transaction": "/execution/id/321312",
                          "server_name": "localhost:8080",
                          "extra": {
                            "Namespace": "{{execution.namespace}}",
                            "Flow ID": "{{execution.flowId}}",
                            "Execution ID": "{{execution.id}}",
                            "Execution Status": "{{execution.state.current}}",
                            "Link": "{{link}}"
                          }
                      }
                """
        ),
    }
)
public class SentryAlert extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Sentry event URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Sentry event payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Sentry event: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
