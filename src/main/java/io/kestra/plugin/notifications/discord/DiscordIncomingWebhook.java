package io.kestra.plugin.notifications.discord;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Discord message using an Incoming Webhook.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://discord.com/developers/docs/resources/webhook\">Discord documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Discord notification on a failed flow execution.",
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
            title = "Send a Discord message via incoming webhook.",
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
                                "title": "Hello from Kestra",
                                "color": 16777215
                                "description": "This is a test message from Kestra",
                                "footer": {
                                    "text": "Footer text"
                                }
                            }
                        ]
                      }
                """
        ),
    }
)
public class DiscordIncomingWebhook extends AbstractHttpOptionsTask {

    @Schema(
        title = "Webhook URL which should be taken from discord integrations tab."
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

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            var payload = JacksonMapper.ofJson() // explicitly pass it as a JsonNode to HttpRequest to avoid encoding issues
                .readTree(
                    runContext.render(this.payload).as(String.class).orElse(null)
                );

            runContext.logger().debug("Send Discord webhook: {}", payload);

            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.JsonRequestBody.builder()
                    .content(payload)
                    .build());

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() == 200) {
                runContext.logger().info("Request succeeded");
            }
        }

        return null;
    }
}
