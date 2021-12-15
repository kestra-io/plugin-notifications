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
    title = "Task to send a mail with execution information",
    description = "Main execution information are provided in the sent mail (id, namespace, flow, state, duration, start date ...)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a mail notification on failed flow",
            full = true,
            code = {
                "id: mail",
                "namespace: io.kestra.tests",
                "",
                "listeners:",
                "  - conditions:",
                "      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "        in:",
                "          - FAILED",
                "    tasks:",
                "      - id: mail",
                "        type: io.kestra.plugin.notifications.mail.MailExecution",
                "        to: to@mail.com",
                "        from: from@mail.com",
                "        subject: This is the subject",
                "        host: nohost-mail.site",
                "        port: 465",
                "        username: user",
                "        password: pass",
                "        sessionTimeout: 1000",
                "        transportStrategy: SMTPS",
                "",
                "tasks:",
                "  - id: ok",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
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
