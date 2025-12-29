package io.kestra.plugin.notifications.squadcast;

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
@Deprecated
@Schema(
    title = "Send a Squadcast message with the execution information.",
    description = """
        The message will include execution details such as ID, namespace, flow name, start date, duration, and status.

        Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting).
        Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [SquadcastIncomingWebhook](https://kestra.io/plugins/plugin-notifications/tasks/squadcast/io.kestra.plugin.notifications.squadcast.squadcastincomingwebhook) task.

        This task is deprecated since Kestra v1.1.11 and has been replaced by `plugin-squadcast (io.kestra.plugin.squadcast)`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send a [Squadcast](https://www.squadcast.com/) alert via [incoming webhook](https://support.squadcast.com/integrations/incident-webhook-incident-webhook-api)",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.squadcast.SquadcastExecution
                    url: "{{ secret('SQUADCAST_WEBHOOK') }}" # format: https://api.squadcast.com/v2/incidents/api/xyzs
                    message: "Kestra Squadcast alert"
                    priority: P1
                    eventId: "6"
                    status: trigger
                    tags:
                      severity: high
                      tagName1: tagValue1
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
                        namespace: prod
                        prefix: true
                """
        )
    }
)
public class SquadcastExecution extends SquadcastTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("squadcast-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
