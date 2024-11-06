package io.kestra.plugin.notifications.slack;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import jakarta.validation.constraints.NotEmpty;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Slack message using an Incoming Webhook",
    description = "Add this task to send direct Slack notifications. Check the <a href=\"https://api.slack.com/messaging/webhooks\">Slack documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack notification on a failed flow execution",
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
            title = "Send a Slack message via incoming webhook with a text argument",
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
            title = "Send a Slack message via incoming webhook with a blocks argument, read more on blocks <a href=\"https://api.slack.com/reference/block-kit/blocks\">here</a>",
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
            title = "Send a [Rocket.Chat](https://www.rocket.chat/) message via [incoming webhook](https://docs.rocket.chat/docs/integrations#incoming-webhook-script)",
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
public class SlackIncomingWebhook extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Slack incoming webhook URL",
        description = "Check the <a href=\"https://api.slack.com/messaging/webhooks#create_a_webhook\">Create an Incoming Webhook</a> documentation for more details.."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String url;

    @Schema(
        title = "Slack message payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            //First render to get the template, second render to populate the payload
            String payload = runContext.render(runContext.render(this.payload).as(String.class).orElse(null));

            runContext.logger().debug("Send Slack webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
