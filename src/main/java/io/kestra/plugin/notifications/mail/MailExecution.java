package io.kestra.plugin.notifications.mail;

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
    title = "Task to send an email with the execution information",
    description = "Flow execution information is provided in the sent email including xxecution metadata such as ID, namespace, flow, state, duration, start date, and the URL to the flow's execution page"
)
@Plugin(
    examples = {
        @Example(
            title = "Send an email notification on failed flow execution",
            full = true,
            code = {
                "id: emailAlert",
                "namespace: dev",
                "",
                "listeners:",
                "  - conditions:",
                "      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "        in:",
                "          - FAILED",
                "    tasks:",
                "      - id: email",
                "        type: io.kestra.plugin.notifications.mail.MailExecution",
                "        to: hello@kestra.io",
                "        from: hello@kestra.io",
                "        subject: This is the subject",
                "        host: mail.privateemail.com",
                "        port: 465",
                "        username: hello@kestra.io",
                "        password: topSecret42",
                "        sessionTimeout: 1000",
                "        transportStrategy: SMTPS",
                "",
                "tasks:",
                "  - id: alwaysFail",
                "    type: io.kestra.core.tasks.executions.Fail"
            }
        )
    }
)
public class MailExecution extends MailTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "mail-template.hbs.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
