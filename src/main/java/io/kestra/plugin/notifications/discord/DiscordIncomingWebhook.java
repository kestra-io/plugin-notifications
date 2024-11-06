package io.kestra.plugin.notifications.discord;

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
    title = "Send a Discord message using an Incoming Webhook",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://discord.com/developers/docs/resources/webhook\">Discord documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Discord notification on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.discord.DiscordIncomingWebhook
                    url: "{{ secret('DISCORD_WEBHOOK') }}" # https://discord.com/api/webhooks/000000/xxxxxxxxxxx
                    payload: |
                      {
                        "username": "MyUsername",
                        "content": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                        "embedList": [{
                                "title": "Discord Notification"
                            }]
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
                  - id: send_discord_message
                    type: io.kestra.plugin.notifications.discord.DiscordIncomingWebhook
                    url: "{{ secret('DISCORD_WEBHOOK') }}"
                    payload: |
                      {
                        "username": "MyUsername",
                        "tts": false,
                        "content": "Hello from the workflow {{ flow.id }}",
                        "embeds": [
                            {
                                "title": "Discord Hello",
                                "color": 16777215
                                "description": "Namespace: dev\nFlow ID: discord\nExecution ID: 1p0JVFz24ZVLSK8iJN6hfs\nExecution Status: SUCCESS\n\n[Link to the Execution page](http://localhost:8080/ui/executions/dev/discord/1p0JVFz24ZVLSK8iJN6hfs)",
                                "footer": {
                                    "text": "Succeeded after 00:00:00.385"
                                }
                            }
                        ]
                      }
                """
        ),
    }
)
public class DiscordIncomingWebhook extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Webhook URL which should be taken from discord integrations tab"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Discord message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            //First render to get the template, second render to populate the payload
            String payload = runContext.render(runContext.render(this.payload).as(String.class).orElse(null));

            runContext.logger().debug("Send Discord webhook: {}", payload);

            client.toBlocking().exchange(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
