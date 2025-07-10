package io.kestra.plugin.notifications.sendgrid;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Send a SendGrid email with the execution information.",
    description = "The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration, and the final status of the execution. If failed, then the task that led to the failure is specified.\n\n" +
    "Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting), as shown in this example. Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [SendGridMailSend](https://kestra.io/plugins/plugin-notifications/tasks/mail/io.kestra.plugin.notifications.gendgrid.sendgridmailsend) task."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an SendGrid email notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.sendgrid.SendGridMailExecution
                    to:
                      - hello@kestra.io
                    from: hello@kestra.io
                    subject: "The workflow execution {{trigger.executionId}} failed for the flow {{trigger.flowId}} in the namespace {{trigger.namespace}}"
                    sendgridApiKey: "{{ secret('SENDGRID_API_KEY') }}"
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
public class SendGridMailExecution extends SendGridMailTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public SendGridMailSend.Output run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("sendgrid-mail-template.hbs.peb");
        this.textTemplateUri = Property.ofValue("sendgrid-text-template.hbs.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
