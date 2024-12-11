package io.kestra.plugin.notifications.opsgenie;

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
    title = "Send a notification with the execution information via Opsgenie",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration and the final status of the execution, and (if failed) the task that led to a failure.\n\n" +
        "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [OpsgenieAlert](https://kestra.io/plugins/plugin-notifications/tasks/opsgenie/io.kestra.plugin.notifications.opsgenie.opsgeniealert) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send notification on a failed flow execution via Opsgenie",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.opsgenie.OpsgenieExecution
                    url: "{{ secret('OPSGENIE_REQUEST') }}" # format: 'https://api.opsgenie.com/v2/alerts/requests/xxxxxxyx-yyyx-xyxx-yyxx-yyxyyyyyxxxx'
                    message: "Kestra Opsgenie alert"
                    alias: ExecutionError
                    responders:
                      4513b7ea-3b91-438f-b7e4-e3e54af9147c: team
                      bb4d9938-c3c2-455d-aaab-727aa701c0d8: user
                      aee8a0de-c80f-4515-a232-501c0bc9d715: escalation
                      80564037-1984-4f38-b98e-8a1f662df552: schedule
                    visibleTo:
                      4513b7ea-3b91-438f-b7e4-e3e54af9147c: team
                      bb4d9938-c3c2-455d-aaab-727aa701c0d8: user
                    priority: P1
                    tags:
                      - ExecutionError
                      - Error
                      - Fail
                      - Execution
                    authorizationToken: sampleAuthorizationToken
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
public class OpsgenieExecution extends OpsgenieTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.of("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.of("opsgenie-template.peb");
        this.templateRenderMap = Property.of(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
