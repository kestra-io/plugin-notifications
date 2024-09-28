package io.kestra.plugin.notifications.slack;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.ExecutionInterface;
import io.kestra.plugin.notifications.services.ExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Slack message with the execution information",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration and the final status of the execution, and (if failed) the task that led to a failure.\n\n" +
    "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [SlackIncomingWebhook](https://kestra.io/plugins/plugin-notifications/tasks/slack/io.kestra.plugin.notifications.slack.slackincomingwebhook) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack notification on a failed flow execution",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.slack.SlackExecution
                    url: "{{ secret('SLACK_WEBHOOK') }}" # format: https://hooks.slack.com/services/xzy/xyz/xyz
                    channel: "#general"
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatusCondition
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespaceCondition
                        namespace: prod
                        prefix: true
                """
        ),
        @Example(
            title = "Send a [Rocket.Chat](https://www.rocket.chat/) notification on a failed flow execution",
            full = true,
            code = """
                id: failure_alert
                namespace: debug

                tasks:
                  - id: send_alert_to_rocket_chat
                    type: io.kestra.plugin.notifications.slack.SlackExecution
                    url: "{{ secret('ROCKET_CHAT_WEBHOOK') }}"
                    channel: "#errors"
                    executionId: "{{ trigger.executionId }}"
                    username: "Kestra TEST"
                    iconUrl: "https://avatars.githubusercontent.com/u/59033362?s=48"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatusCondition
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespaceCondition
                        namespace: debug
                        prefix: true
                """
        )        
    }
)
public class SlackExecution extends SlackTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "slack-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
