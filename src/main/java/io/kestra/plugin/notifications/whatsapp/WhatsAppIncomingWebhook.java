package io.kestra.plugin.notifications.whatsapp;

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
    title = "Send a WhatsApp message using an Incoming Webhook.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://developers.facebook.com/docs/whatsapp/on-premises/guides/webhooks\">WhatsApp documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a WhatsApp notification on a failed flow execution.",
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
                    type: io.kestra.plugin.notifications.whatsapp.WhatsAppIncomingWebhook
                    url: "{{ secret('WHATSAPP_WEBHOOK') }}" # https://webhook.your-domain
                    payload: |
                      {
                        "profileName": "MyName",
                        "whatsAppIds": ["IdNo1, IdNo2"],
                        "from": 380999999999
                      }
                """
        ),
        @Example(
            title = "Send a WhatsApp message via incoming webhook.",
            full = true,
            code = """
                id: whatsapp_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_whatsapp_message
                    type: io.kestra.plugin.notifications.whatsapp.WhatsAppIncomingWebhook
                    url: "{{ secret('WHATSAPP_WEBHOOK') }}"
                    payload: |
                      {
                        "profileName": "MyName",
                        "whatsAppIds": ["IdNo1, IdNo2"],
                        "from": 380999999999,
                        "messageId": "wamIdNo1"
                      }
                """
        ),
    }
)
public class WhatsAppIncomingWebhook extends AbstractHttpOptionsTask {

    @Schema(
        title = "Webhook URL which should be taken from whatsapp integrations tab"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "WhatsApp message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(this.payload).as(String.class).orElse(null);

            runContext.logger().debug("Send WhatsApp webhook: {}", payload);
            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
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
