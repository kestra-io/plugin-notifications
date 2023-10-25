package io.kestra.plugin.notifications.zenduty;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Zenduty alert",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. " +
        "Check the [Zenduty integration documentation](https://docs.zenduty.com/docs/api) and " +
        "the [Zenduty Events API specification](https://apidocs.zenduty.com/#tag/Events/paths/~1api~1events~1%7Bintegration_key%7D~1/post) for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Zenduty alert on a failed flow execution. Make sure that the payload follows the [Zenduty Events API specification](https://apidocs.zenduty.com/#tag/Events/paths/~1api~1events~1%7Bintegration_key%7D~1/post), including the `message` and `alert_type` payload properties, which are required.",
            full = true,
            code = """
                id: unreliable_flow
                namespace: prod

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.notifications.zenduty.ZendutyAlert
                    url: "https://www.zenduty.com/api/events/{{ secret('ZENDUTY_INTEGRATION_KEY') }}/"
                    payload: |
                      {
                        "alert_type": "info",
                        "message": "This is info alert",
                        "summary": "This is the incident summary",
                        "suppressed": false,
                        "entity_id": 12345,
                        "payload": {
                            "status": "ACME Payments are failing",
                            "severity": "1",
                            "project": "kubeprod"
                          },
                        "urls": [
                          {
                            "link_url": "https://www.example.com/alerts/12345/",
                            "link_text": "Alert URL"
                          }
                        ]
                      }
                """
        )
    }
)
public class ZendutyAlert extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Zenduty API endpoint"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Zenduty alert request payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Zenduty webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
