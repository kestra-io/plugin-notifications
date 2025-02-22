package io.kestra.plugin.notifications.zulip;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
    title = "Send a Zulip message using an Incoming Webhook",
    description = "Add this task to send direct Zulip notifications. Check the <a href=\"https://api.zulip.com/messaging/webhooks\">Zulip documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Zulip notification on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.zulip.ZulipIncomingWebhook
                    url: "{{ secret('ZULIP_WEBHOOK') }}" # https://yourZulipDomain.zulipchat.com/api/v1/external/INTEGRATION_NAME?api_key=API_KEY
                    payload: |
                      {
                        "text": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                      }
                """
        ),
        @Example(
            title = "Send a Zulip message via incoming webhook with a text argument",
            full = true,
            code = """
                id: zulip_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_zulip_message
                    type: io.kestra.plugin.notifications.zulip.ZulipIncomingWebhook
                    url: "{{ secret('ZULIP_WEBHOOK') }}" # https://yourZulipDomain.zulipchat.com/api/v1/external/INTEGRATION_NAME?api_key=API_KEY
                    payload: |
                      {
                        "text": "Hello from the workflow {{ flow.id }}"
                      }
                """
        ),
        @Example(
            title = "Send a Zulip message via incoming webhook with a blocks argument, read more on blocks <a href=\"https://api.zulip.com/reference/block-kit/blocks\">here</a>",
            full = true,
            code = """
                id: zulip_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_zulip_message
                    type: io.kestra.plugin.notifications.zulip.ZulipIncomingWebhook
                    url: "{{ secret('ZULIP_WEBHOOK') }}" # format: https://yourZulipDomain.zulipchat.com/api/v1/external/INTEGRATION_NAME?api_key=API_KEY
                    payload: |
                      {
                        "blocks": [
                            {
                                "type": "section",
                                "text": {
                                    "type": "mrkdwn",
                                    "text": "Hello from the workflow *{{ flow.id }}*"
                                }
                            }
                        ]
                      }
                """
        ),
    }
)
public class ZulipIncomingWebhook extends AbstractHttpOptionsTask {
    @Schema(
        title = "Zulip incoming webhook URL",
        description = "Check the <a href=\"https://zulip.com/api/incoming-webhooks-overview\">Incoming Webhook Integrations</a> documentation for more details.."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String url;

    @Schema(
        title = "Zulip message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            //First render to get the template, second render to populate the payload
            String payload = runContext.render(runContext.render(this.payload).as(String.class).orElse(null));

            runContext.logger().debug("Send Zulip webhook: {}", payload);
            HttpRequest request = HttpRequest.builder()
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
                    .content(payload)
                    .build())
                .build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() == 200) {
                runContext.logger().info("Request succeeded");
            }
        }

        return null;
    }
}
