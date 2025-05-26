package io.kestra.plugin.notifications.discord;

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
    title = "Send a Discord message with the execution information.",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration, and the final status of the execution. If failed, then the task that led to the failure is specified.\n\n" +
        "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [DiscordIncomingWebhook](https://kestra.io/plugins/plugin-notifications/tasks/discord/io.kestra.plugin.notifications.discord.discordincomingwebhook) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Discord notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.discord.DiscordExecution
                    url: "{{ secret('DISCORD_WEBHOOK') }}" # format: https://hooks.discord.com/services/xzy/xyz/xyz
                    username: "MyUsername"
                    embedList:
                        - title: "Discord Notification"
                          color:
                              - 255
                              - 255
                              - 255
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
        )
    }
)
public class DiscordExecution extends DiscordTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = new Property<>("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {

        this.templateUri = Property.ofValue("discord-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
