package io.kestra.plugin.notifications.line;

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
    title = "Send a LINE message with the execution information.",
    description = "Send execution details via Line notification including execution link, ID, namespace, flow name, start date, duration, and status." 
)
@Plugin(
    examples = {
        @Example(
            title = "Send a LINE notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert_line
                namespace: company.team

                tasks:
                  - id: send_line_alert
                    type: io.kestra.plugin.notifications.line.LineExecution
                    channelAccessToken: "{{ secret('LINE_CHANNEL_ACCESS_TOKEN') }}"
                    recipientIds:
                      - "LINE_USER_ID_1"
                      - "LINE_USER_ID_2"
                    executionId: "{{ trigger.executionId }}"
                    customMessage: "Production workflow failed - immediate attention required!"
                    customFields:
                      Environment: "Production"
                      Team: "DevOps"
                      Priority: "High"

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
            title = "Send a LINE notification with custom fields.",
            full = true,
            code = """
                id: line_notification_with_custom_fields
                namespace: company.team

                tasks:
                  - id: send_line_notification
                    type: io.kestra.plugin.notifications.line.LineExecution
                    channelAccessToken: "{{ secret('LINE_CHANNEL_ACCESS_TOKEN') }}"
                    recipientIds:
                      - "LINE_USER_ID_1"
                    executionId: "{{ trigger.executionId }}"
                    customMessage: "Data pipeline execution completed"
                    customFields:
                      Environment: "Production"
                      Region: "Asia-Pacific"
                      Records Processed: "{{ outputs.process_data.recordsProcessed }}"
                      Duration: "{{ trigger.execution.duration }}"

                triggers:
                  - id: success_notifications
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - SUCCESS
                """
        )
    }
)
public class LineExecution extends LineTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("line-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}