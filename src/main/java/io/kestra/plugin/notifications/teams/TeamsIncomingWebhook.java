package io.kestra.plugin.notifications.teams;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.HttpRequest;
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
    title = "Send a Microsoft Teams message using an incoming webhook.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure noticiations. Check the <a href=\"https://learn.microsoft.com/en-us/azure/data-factory/how-to-send-notifications-to-teams?tabs=data-factory\">Microsoft Teams documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Microsoft Teams notification on a failed flow execution",
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
                    type: io.kestra.plugin.notifications.teams.TeamsIncomingWebhook
                    url: "{{ secret('TEAMS_WEBHOOK') }}" # format: https://microsoft.webhook.office.com/webhook/xyz
                    payload: |
                      {
                        "@type": "MessageCard",
                        "@context": "http://schema.org/extensions",
                        "themeColor": "0076D7",
                        "summary": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}",
                        "sections": [{
                        "activityTitle": "Kestra Workflow Notification",
                        "activitySubtitle": "Workflow Execution Finished With Errors",
                        "markdown": true
                        }],
                        "potentialAction": [
                          {
                            "@type": "OpenUri",
                            "name": "Kestra Workflow",
                            "targets": [
                            {
                            "os": "default",
                            "uri": "{{ vars.systemUrl }}"
                            }
                            ]
                          }
                        ]
                      }
                """
        ),
        @Example(
            title = "Send a Microsoft Teams notification message",
            code = {
                "url: \"https://microsoft.webhook.office.com/webhookb2/XXXXXXXXXX\"",
                "payload: |",
                "  {",
                "    \"@type\": \"MessageCard\",",
                "    \"@context\": \"http://schema.org/extensions\",",
                "    \"themeColor\": \"0076D7\",",
                "    \"summary\": \"Notification message\",",
                "    \"sections\": [{",
                "      \"activityTitle\": \"Rolling Workflow started\",",
                "      \"activitySubtitle\": \"Workflow Notification\",",
                "      \"markdown\": true",
                "    }],",
                "    \"potentialAction\": [",
                "      {",
                "        \"@type\": \"OpenUri\",",
                "        \"name\": \"Rolling Workflow\",",
                "        \"targets\": [",
                "          {",
                "           \"os\": \"default\",",
                "           \"uri\": \"{{ vars.systemUrl }}\"",
                "          }",
                "        ]",
                "      }",
                "    ]",
                "  }"
            }
        )
    }
)
public class TeamsIncomingWebhook  extends AbstractHttpOptionsTask {
    @Schema(
        title = "Microsoft Teams incoming webhook URL"
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String url;

    @Schema(
        title = "Microsoft Teams message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(this.payload).as(String.class).orElse(null);

            runContext.logger().debug("Send Microsoft Teams webhook: {}", payload);
            HttpRequest request = HttpRequest.builder()
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(io.kestra.core.http.HttpRequest.StringRequestBody.builder()
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
