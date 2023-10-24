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
        "Check the <a href=\"https://docs.zenduty.com/docs/api?_gl=1*1lvd8l5*_ga*ODQ4MjkwMjQuMTY5ODEzODExNQ..*_ga_12ECK65TRM*MTY5ODEzODEzNC4xLjEuMTY5ODEzOTYzMy42MC4wLjA.\">Zenduty integration documentation</a> and" +
        "<a href=\"https://apidocs.zenduty.com/#tag/Events/paths/~1api~1events~1{integration_key}~1/post\">Zenduty event documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Zenduty alert on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.zenduty.ZendutyAlert
                    url: "https://www.zenduty.com/api/events/{{ secret('ZENDUTY_INTEGRATION_KEY') }}/" # https://www.zenduty.com/api/events/x1111xxx-x1x1-111x-1111-xx1xx11x1x1x/
                    payload: |
                      {
                          "message": "Execution error",
                          "entity_id": "191f5e2c-515e-4ee0-b501-3a292f8dae2f",
                          "alert_type": "error",
                          "payload":
                             {
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
        ),
        @Example(
            title = "Send a Zenduty alert",
            full = true,
            code = """
                id: zenduty_alert
                namespace: dev

                tasks:
                  - id: send_zenduty_message
                    type: io.kestra.plugin.notifications.zenduty.ZendutyAlert
                    url: "https://www.zenduty.com/api/events/{{ secret('ZENDUTY_INTEGRATION_KEY') }}/"
                    payload: |
                      {
                          "message": "Execution error",
                          "entity_id": "191f5e2c-515e-4ee0-b501-3a292f8dae2f",
                          "alert_type": "error",
                          summary: "Summary of the execution"
                      }
                """
        ),
    }
)
public class ZendutyAlert extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Zenduty alert URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Zenduty alert payload"
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
