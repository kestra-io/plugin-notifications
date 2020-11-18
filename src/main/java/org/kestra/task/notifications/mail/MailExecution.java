package org.kestra.task.notifications.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;
import org.kestra.core.serializers.JacksonMapper;

import java.util.HashMap;
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
                "namespace: org.kestra.tests",
                "",
                "listeners:",
                "  - conditions:",
                "      - type: org.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "        in:",
                "          - FAILED",
                "    tasks:",
                "      - id: mail",
                "        type: org.kestra.task.notifications.mail.MailExecution",
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
                "",
                "tasks:",
                "  - id: ok",
                "    type: org.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\""
            }
        )
    }
)
public class MailExecution extends MailTemplate {
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        @SuppressWarnings("unchecked")
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        this.templateUri = "mail-template.hbs.html";

        this.templateRenderMap = new HashMap<>();
        this.templateRenderMap.put("duration", execution.getState().humanDuration());
        this.templateRenderMap.put("startDate", execution.getState().getStartDate());
        // FIXME
        this.templateRenderMap.put("link", "https://todo.com");

        execution
            .findFirstByState(State.Type.FAILED)
            .ifPresentOrElse(
                taskRun -> this.templateRenderMap.put("firstFailed", taskRun),
                () -> this.templateRenderMap.put("firstFailed", false)
            );

        return super.run(runContext);
    }
}
