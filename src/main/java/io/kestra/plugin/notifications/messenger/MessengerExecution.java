package io.kestra.plugin.notifications.messenger;

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
    title = "Send a Messenger message with execution information.",
    description = "Send execution details via Facebook Messenger including execution link, ID, namespace, flow name, start date, duration, and status."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Messenger notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_messenger_alert
                    type: io.kestra.plugin.notifications.messenger.MessengerExecution
                    pageId: "9876543214587"
                    accessToken: "{{ secret('MESSENGER_ACCESS_TOKEN') }}"
                    recipientIds:
                      - "24745216345137108"
                    executionId: "{{trigger.executionId}}"
                    customMessage: "Production workflow failed!"

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
public class MessengerExecution extends MessengerTemplate implements ExecutionInterface {

    @Schema(
        title = "The execution id to use",
        description = "Default is the current execution, change it to {{ trigger.executionId }} if you use this task with a Flow trigger to use the original execution."
    )
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(
        title = "Custom fields to be added on notification"
    )
    private Property<Map<String, Object>> customFields;

    @Schema(
        title = "Custom message to be added on notification"
    )
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("messenger-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));
        return super.run(runContext);
    }
}