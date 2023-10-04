package io.kestra.plugin.notifications.zenduty;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Zenduty message using an Alert API",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://apidocs.zenduty.com/#tag/Integration-Alerts/paths/~1api~1account~1teams~1%7Bteam_id%7D~1services~1%7Bservice_id%7D~1integrations~1%7Bintegration_id%7D~1alerts~1/get\">Zenduty documentation</a> for more details.."
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
                    url: "{{ secret('ZENDUTY_ALERT') }}" # https://www.zenduty.com/api/incidents/
                    payload: |
                      {
                          "title": "Execution error",
                          "service": "191f5e2c-515e-4ee0-b501-3a292f8dae2f"
                      }
                    bearerAuth: xxx000yyy111
                """
        ),
        @Example(
            title = "Send a Zenduty message via alert API",
            full = true,
            code = """
                id: zenduty_alert
                namespace: dev

                tasks:
                  - id: send_zenduty_message
                    type: io.kestra.plugin.notifications.zenduty.ZendutyAlert
                    url: "{{ secret('ZENDUTY_ALERT') }}"
                    payload: |
                      {
                          "title": "Execution",
                          "service": "191f5e2c-515e-4ee0-b501-3a292f8dae2f"
                      }
                    bearerAuth: xxx000yyy111
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


    @Schema(
        title = "Zenduty bearer token"
    )
    @PluginProperty(dynamic = true)
    protected String bearerAuth;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Zenduty webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload)
                .header(HttpHeaders.AUTHORIZATION, "Bearer "+runContext.render(bearerAuth)));
        }

        return null;
    }
}
