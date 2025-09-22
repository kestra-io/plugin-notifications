package io.kestra.plugin.notifications.x;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Send a tweet/post to X (formerly Twitter) with execution information.",
    description =
        """
        The tweet will include execution details such as status, flow name, namespace, duration, and a link to the execution page in the UI.
        Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting).
        Don't use this notification task in `errors` tasks.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send a tweet notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert_x
                namespace: company.team

                tasks:
                  - id: post_on_x
                    type: io.kestra.plugin.notifications.x.XExecution
                    bearerToken: "{{ secret('X_API_BEARER_TOKEN') }}"
                    customMessage: "⚠️ Flow '{{ flow.namespace }}.{{ flow.id }}' failed in {{ execution.duration }} seconds. Status: {{ execution.status }} #Kestra"

                triggers:
                  - id: failed_flows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                """
        ),
        @Example(
            title = "Send a success notification with custom fields.",
            full = true,
            code = """
                id: success_alert_x
                namespace: company.team

                tasks:
                  - id: post_success
                    type: io.kestra.plugin.notifications.x.XExecution
                    bearerToken: "{{ secret('X_API_BEARER_TOKEN') }}"
                    customMessage: "✅ Data pipeline completed successfully! #DataOps"
                    customFields:
                      Environment: "{{ env.ENVIRONMENT }}"
                      Records: "{{ outputs.process_records.count }}"

                triggers:
                  - id: successful_flows
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

    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(
        title = "Custom message for the tweet",
        description = "Custom message to override the default execution template. If not provided, the default x-template.peb template will be used with execution details."
    )
    @PluginProperty(dynamic = true)
    private Property<String> customMessage;

    @Schema(
        title = "Custom fields to include in the tweet",
        description = "Additional custom fields to include in the tweet template. These will be available in the x-template.peb template as variables."
    )
    @PluginProperty
    private Property<Map<String, Object>> customFields;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        super.templateUri = Property.ofValue("x-template.peb");
        super.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }

}
