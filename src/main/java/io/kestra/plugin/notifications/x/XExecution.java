package io.kestra.plugin.notifications.x;

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
    title = "Send an X (Twitter) post with execution information.",
    description = "Send execution details via X (Twitter) including execution link, ID, namespace, flow name, start date, duration, and status."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an X notification on a failed flow execution using Bearer Token.",
            full = true,
            code = """
                id: failure_alert_x
                namespace: company.team

                tasks:
                  - id: send_x_alert
                    type: io.kestra.plugin.notifications.x.XExecution
                    bearerToken: "{{ secret('X_BEARER_TOKEN') }}"
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
            title = "Send an X notification using OAuth 1.0a credentials.",
            full = true,
            code = """
                id: success_alert_x
                namespace: company.team

                tasks:
                  - id: send_x_success
                    type: io.kestra.plugin.notifications.x.XExecution
                    consumerKey: "{{ secret('X_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('X_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('X_ACCESS_TOKEN') }}"
                    accessSecret: "{{ secret('X_ACCESS_SECRET') }}"
                    executionId: "{{ trigger.executionId }}"
                    customMessage: "Deployment completed successfully!"

                triggers:
                  - id: successful_deployments
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - SUCCESS
                """
        )
    }
)
public class XExecution extends XTemplate implements ExecutionInterface {

    @Schema(title = "The execution ID to use", description = "Default is the current execution, change it to {{ trigger.executionId }} if you use this task with a Flow trigger to use the original execution.")
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(title = "Custom fields to be added in the notification")
    private Property<Map<String, Object>> customFields;

    @Schema(title = "Custom message to be added in the notification")
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("x-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));
        return super.run(runContext);
    }
}
