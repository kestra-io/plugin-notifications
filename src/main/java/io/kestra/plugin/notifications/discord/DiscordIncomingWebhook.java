package io.kestra.plugin.notifications.discord;

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
                namespace: prod

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
                        "title": "Discord Alert",
                        "content": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                      }
                """
        ),
        @Example(
            title = "Send a Discord message via incoming webhook",
            full = true,
            code = """
                id: discord_incoming_webhook
                namespace: dev

                tasks:
                  - id: send_discord_message
                    type: io.kestra.plugin.notifications.discord.DiscordIncomingWebhook
                    url: "{{ secret('DISCORD_WEBHOOK') }}"
                    payload: |
                      {
                        "username": "MyUsername",
                        "title": "Discord Hello",
                        "content": "Hello from the workflow {{ flow.id }}"
                      }            
                """
        ),
    }
)
public class DiscordIncomingWebhook extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Webhook url which should be taken from discord integrations tab"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Discord message payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Slack webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
