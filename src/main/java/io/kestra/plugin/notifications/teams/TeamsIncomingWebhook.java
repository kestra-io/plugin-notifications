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
@Deprecated
@Schema(
    title = "Send a Microsoft Teams message using an Incoming Webhook.",
    description = """
        Add this task to a list of `errors` tasks to implement custom flow-level failure noticiations. Check the [Microsoft Teams documentation](https://support.microsoft.com/en-us/office/create-incoming-webhooks-with-workflows-for-microsoft-teams-8ae491c7-0394-4861-ba59-055e33f75498) for more details.

        This task is deprecated and has been replaced by `plugin-microsoft365 (io.kestra.plugin.microsoft365.teams)`.
        """
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
                          "type": "message",
                          "attachments": [
                            {
                              "contentType": "application/vnd.microsoft.card.adaptive",
                              "content": {
                                "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                                "type": "AdaptiveCard",
                                "version": "1.4",
                                "body": [
                                  {
                                    "type": "TextBlock",
                                    "size": "Large",
                                    "weight": "Bolder",
                                    "text": "Kestra Execution Notification"
                                  },
                                  {
                                    "type": "TextBlock",
                                    "text": "Execution ID: `{{ execution.id }}`",
                                    "wrap": true
                                  },
                                  {
                                    "type": "TextBlock",
                                    "text": "Flow: `{{ flow.id }}` in namespace `{{ flow.namespace }}`",
                                    "wrap": true
                                  },
                                  {
                                    "type": "TextBlock",
                                    "text": "Status: **{{ execution.state }}**",
                                    "wrap": true
                                  }
                                ],
                                "actions": [
                                  {
                                    "type": "Action.OpenUrl",
                                    "title": "View Execution",
                                    "url": "{{ kestra.url }}/ui/executions/{{ flow.namespace }}/{{ flow.id }}/{{ execution.id }}"
                                  }
                                ]
                              }
                            }
                        ]
                        }
                """
        ),
        @Example(
            title = "Send a Microsoft Teams notification message.",
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
            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(io.kestra.core.http.HttpRequest.StringRequestBody.builder()
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
