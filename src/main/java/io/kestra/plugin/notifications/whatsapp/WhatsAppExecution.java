package io.kestra.plugin.notifications.whatsapp;

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
    title = "Send a WhatsApp message with the execution information",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration and the final status of the execution, and (if failed) the task that led to a failure.\n\n" +
        "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [WhatsAppIncomingWebhook](https://kestra.io/plugins/plugin-notifications/tasks/discord/io.kestra.plugin.notifications.discord.discordincomingwebhook) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a WhatsApp notification on a failed flow execution",
            full = true,
            code = """
                id: failure_alert
                namespace: prod.monitoring

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.discord.WhatsAppExecution
                    url: "{{ secret('WHATSAPP_WEBHOOK') }}" # format: https://hooks.discord.com/services/xzy/xyz/xyz
                    profileName: "MyProfile"
                    from: 380999999999
                    whatsAppIds:
                        - "some waId"
                        - "waId No2"
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.core.models.triggers.types.Flow
                    conditions:
                      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.core.models.conditions.types.ExecutionNamespaceCondition
                        namespace: prod
                        prefix: true
                """
        )
    }
)
public class WhatsAppExecution extends WhatsAppTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "whatsapp-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
