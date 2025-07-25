package io.kestra.plugin.notifications.slack;

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
    title = "Send a Slack message using an Incoming Webhook.",
    description = "Add this task to send direct Slack notifications. Check the <a href=\"https://api.slack.com/messaging/webhooks\">Slack documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack notification on a failed flow execution.",
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
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ secret('SLACK_WEBHOOK') }}" # https://hooks.slack.com/services/xzy/xyz/xyz
                    payload: |
                      {
                        "text": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                      }
                """
        ),
        @Example(
            title = "Send a Slack message via incoming webhook with a text argument.",
            full = true,
            code = """
                id: slack_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_slack_message
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ secret('SLACK_WEBHOOK') }}"
                    payload: |
                      {
                        "text": "Hello from the workflow {{ flow.id }}"
                      }
                """
        ),
        @Example(
            title = "Send a Slack message via incoming webhook with a blocks argument, read more on blocks <a href=\"https://api.slack.com/reference/block-kit/blocks\">here</a>.",
            full = true,
            code = """
                id: slack_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_slack_message
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ secret('SLACK_WEBHOOK') }}"
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
        @Example(
            title = "Send a Slack message with 'messageText' (handles Slack markdown, no escaping needed)",
            full = true,
            code = """
                id: slack_incoming_webhook
                namespace: company.team

                inputs:
                 - id: prompt
                   type: STRING
                   defaults: Summarize top 5 news from my region.

                tasks:
                 - id: news
                   type: io.kestra.plugin.openai.Responses
                   apiKey: "{{ kv('OPENAI_API_KEY') }}"
                   model: gpt-4.1-mini
                   input: "{{ inputs.prompt }}"
                   toolChoice: REQUIRED
                   tools:
                     - type: web_search_preview
                       search_context_size: low
                       user_location:
                         type: approximate
                         city: Berlin
                         region: Berlin
                         country: DE

                 - id: send_via_slack
                   type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                   url: https://kestra.io/api/mock
                   messageText: "Current news from Berlin: {{ outputs.news.outputText }}"
                """
        ),
        @Example(
            title = "Send a Rocket Chat message via [Slack incoming webhook](https://docs.rocket.chat/docs/integrations#incoming-webhook-script).",
            full = true,
            code = """
                id: rocket_chat_notification
                namespace: company.team
                tasks:
                  - id: send_rocket_chat_message
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ secret('ROCKET_CHAT_WEBHOOK') }}"
                    payload: |
                      {
                        "alias": "Kestra TEST",
                        "avatar": "https://avatars.githubusercontent.com/u/59033362?s=48",
                        "emoji": ":smirk:",
                        "roomId": "#my-channel",
                        "text": "Sample",
                        "tmshow": true,
                        "attachments": [
                          {
                            "collapsed": false,
                            "color": "#ff0000",
                            "text": "Yay!",
                            "title": "Attachment Example",
                            "title_link": "https://rocket.chat",
                            "title_link_download": false,
                            "fields": [
                              {
                                "short": false,
                                "title": "Test title",
                                "value": "Test value"
                              },
                              {
                                "short": true,
                                "title": "Test title",
                                "value": "Test value"
                              }
                            ]
                          }
                        ]
                      }
                """
        ),
    }
)
public class SlackIncomingWebhook extends AbstractHttpOptionsTask {
    @Schema(
        title = "Slack incoming webhook URL",
        description = "Check the <a href=\"https://api.slack.com/messaging/webhooks#create_a_webhook\">Create an Incoming Webhook</a> documentation for more details."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String url;

    @Schema(
        title = "Slack message payload"
    )
    protected Property<String> payload;

    @Schema(
        title = "Message Text or JSON String",
        description = "The message content as a raw string. It can be plain text with markdown, or a JSON object. If not a valid JSON object, it is automatically wrapped in `{\"text\": \"...\"}`. This property is ignored if the `payload` property is set."
    )
    private Property<String> messageText;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);
        Object payloadObject = prepareMessage(runContext);

        runContext.logger().debug("Send Slack webhook: {}", payloadObject);
        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.JsonRequestBody.builder()
                    .content(payloadObject)
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

    private Object prepareMessage(RunContext runContext) throws Exception {
        if (payload != null) {
            String renderedPayload = runContext.render(payload).as(String.class).orElse(null);
            return JacksonMapper.ofJson().readTree(renderedPayload);
        }

        if (messageText != null) {
            String renderedMessageText = runContext.render(this.messageText).as(String.class).orElseThrow();

            try {
                // first we try as Json for more flexibility
                return JacksonMapper.ofJson().readTree(renderedMessageText);
            } catch (Exception e) {
                // not valid Json, so proceed with markdown text
                renderedMessageText = toSlackMrkdwn(renderedMessageText);
                return JacksonMapper.ofJson().createObjectNode().put("text", renderedMessageText);
            }
        }

        throw new IllegalArgumentException("Either 'messageText' or 'payload' must be provided");
    }

    private String toSlackMrkdwn(String text) {
        if (text == null) return null;
        // for bold text
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "*$1*");
        // for italic text
        text = text.replaceAll("__(.*?)__", "_$1_");
        // for links
        text = text.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<$2|$1>");
        return text;
    }
}
