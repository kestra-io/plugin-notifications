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
    title = "Send a Zenduty alert",
    description = """
                Add this task to a list of `errors` tasks to implement custom flow-level failure notifications.
                
                Check the <a href=\"https://docs.zenduty.com/docs/integrations\">Zenduty documentation</a> to learn how to create an integration. 
                
                The [API integration](https://docs.zenduty.com/docs/api) is the easiest way to get started. This will allow you to send an API call that follows the format: `curl -X POST https://www.zenduty.com/api/events/[integration-key]/ -H 'Content-Type: application/json' -d '{"alert_type":"critical", "message":"Some message", "summary":"some summary", "entity_id":"some_entity_id"}'`.
                
                Visit the Zenduty [Events API documentation](https://apidocs.zenduty.com/#tag/Events) for more details."""
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
                    url: https://www.zenduty.com/api/events/your-integration-key/
                    payload: |
                        {
                            "alert_type": "critical",
                            "message": "Kestra flow {{flow.id}} failed at {{ execution.startDate }}",
                            "summary": "Flow {{ flow.id }} with revision {{ flow.revision }}, from the namespace {{ flow.namespace }}, failed in the execution {{ execution.id }}.",
                            "entity_id": "{{ execution.id }}"
                        }
                """
        )
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
