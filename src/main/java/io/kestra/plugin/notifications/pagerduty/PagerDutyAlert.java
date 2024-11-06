package io.kestra.plugin.notifications.pagerduty;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
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

import jakarta.validation.constraints.NotBlank;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a PagerDuty alert",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://developer.pagerduty.com/docs/ZG9jOjExMDI5NTgx-send-an-alert-event\">PagerDuty documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a PagerDuty alert on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.pagerduty.PagerDutyAlert
                    url: "{{ secret('PAGERDUTY_EVENT') }}" # https://events.pagerduty.com/v2/enqueue
                    payload: |
                      {
                        "dedup_key": "samplekey",
                        "routing_key": "samplekey",
                        "event_action": "trigger",
                        "payload" : {
                            "summary": "PagerDuty alert",
                        }
                      }
                """
        ),
        @Example(
            title = "Send a Discord message via incoming webhook",
            full = true,
            code = """
                id: discord_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_pagerduty_alert
                    type: io.kestra.plugin.notifications.pagerduty.PagerDutyAlert
                    url: "{{ secret('PAGERDUTY_EVENT') }}"
                    payload: |
                      {
                        "dedup_key": "samplekey",
                        "routing_key": "samplekey",
                        "event_action": "acknowledge"
                      }
                """
        ),
    }
)
public class PagerDutyAlert extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "PagerDuty event URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "PagerDuty message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            //First render to get the template, second render to populate the payload
            String payload = runContext.render(runContext.render(this.payload).as(String.class).orElse(null));

            runContext.logger().debug("Send PagerDuty alert: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
