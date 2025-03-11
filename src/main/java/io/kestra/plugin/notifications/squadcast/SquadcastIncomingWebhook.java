package io.kestra.plugin.notifications.squadcast;

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
    title = "Send a Squadcast message using an Incoming Webhook",
    description = "Add this task to send direct Squadcast notifications. Check the <a href=\"https://support.squadcast.com/docs/webhook\">Squadcast documentation</a> for more details."
)
@Plugin(
    examples = {
      @Example(
        title = "Send a [Squadcast](https://www.squadcast.com/) alert via [incoming webhook](https://support.squadcast.com/integrations/incident-webhook-incident-webhook-api)",
        full = true,
        code = """
            id: squadcast_notification
            namespace: company.team
            tasks:
              - id: send_squadcast_message
                type: io.kestra.plugin.notifications.squadcast.SquadcastIncomingWebhook
                url: "{{ secret('SQUADCAST_WEBHOOK') }}"
                payload: |
                  {
                  "message": "Alert from Kestra flow {{ flow.id }}",
                  "description": "Error occurred in task {{ task.id }}",
                  "tags": {
                    "flow": "{{ flow.namespace }}.{{ flow.id }}",
                    "execution": "{{ execution.id }}",
                    "severity": {
                      "color": "#FF0000",
                      "value": "Critical"
                    }
                  },
                  "status": "trigger",
                  "event_id": "{{ execution.id }}"
                }
            """
    )
  }
)
public class SquadcastIncomingWebhook extends AbstractHttpOptionsTask {
    @Schema(
        title = "Squadcast incoming webhook URL",
        description = "Check the <a href=\"https://support.squadcast.com/docs/webhook\">Squadcast Webhook</a> documentation for more details."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String url;

    @Schema(
        title = "Squadcast message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(runContext.render(this.payload).as(String.class).orElse(null));

            runContext.logger().debug("Send Squadcast webhook: {}", payload);
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