package io.kestra.plugin.notifications.google;

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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Google Chat message using an Incoming Webhook",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://developers.google.com/chat/how-tos/webhooks\">Google documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Google Chat notification on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.google.GoogleChatIncomingWebhook
                    url: "{{ secret('GOOGLE_WEBHOOK') }}" # https://chat.googleapis.com/v1/spaces/xzy/messages?threadKey=errorThread
                    payload: |
                      {
                        "text": "Google Chat Alert"
                      }
                """
        ),
        @Example(
            title = "Send a Google Chat message via incoming webhook",
            full = true,
            code = """
                id: google_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_google_chat_message
                    type: io.kestra.plugin.notifications.google.GoogleChatIncomingWebhook
                    url: "{{ secret('GOOGLE_WEBHOOK') }}"
                    payload: |
                      {
                        "text": "Google Chat Hello"
                      }            
                """
        ),
    }
)
public class GoogleChatIncomingWebhook extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Google Chat incoming webhook URL",
        description = "Check the <a href=\"https://developers.google.com/chat/how-tos/webhooks#step_1_register_the_incoming_webhook\">" +
            "Create an Incoming Webhook</a> documentation for more details.."
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Google Chat message payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Google Chat webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
