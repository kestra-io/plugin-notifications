package io.kestra.plugin.notifications.pagerduty;

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
    title = "Send a PagerDuty message with the execution information",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration and the final status of the execution, and (if failed) the task that led to a failure.\n\n" +
        "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [PagerDutyAlert](https://kestra.io/plugins/plugin-notifications/tasks/pagerduty/io.kestra.plugin.notifications.pagerduty.pagerdutyalert) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a PagerDuty notification on a failed flow execution",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.pagerduty.PagerDutyExecution
                    url: "{{ secret('PAGERDUTY_EVENT') }}" # format: https://events.pagerduty.com/v2/enqueue
                    payloadSummary: "PagerDuty Alert"
                    deduplicationKey: "dedupkey"
                    routingKey: "routingkey"
                    eventAction: "acknowledge"
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
        )
    }
)
public class PagerDutyExecution extends PagerDutyTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = new Property<>("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.of("pagerduty-template.peb");
        this.templateRenderMap = Property.of(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
