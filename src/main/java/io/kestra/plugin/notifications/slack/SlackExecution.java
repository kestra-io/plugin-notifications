package io.kestra.plugin.notifications.slack;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Send a Slack message with the execution information.",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration, the final status of the execution, and the last task ID in an execution.\n\n" +
    "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [SlackIncomingWebhook](https://kestra.io/plugins/plugin-notifications/tasks/slack/io.kestra.plugin.notifications.slack.slackincomingwebhook) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.slack.SlackExecution
                    url: "{{ secret('SLACK_WEBHOOK') }}" # format: https://hooks.slack.com/services/xzy/xyz/xyz
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: prod
                        prefix: true
                """
        ),
        @Example(
            title = "Send a [Rocket.Chat](https://www.rocket.chat/) notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: debug

                tasks:
                  - id: send_alert_to_rocket_chat
                    type: io.kestra.plugin.notifications.slack.SlackExecution
                    url: "{{ secret('ROCKET_CHAT_WEBHOOK') }}"
                    executionId: "{{ trigger.executionId }}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: debug
                        prefix: true
                """
        )
    }
)
public class SlackExecution extends SlackTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = new Property<>("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.of("slack-template.peb");
        this.templateRenderMap = Property.of(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
