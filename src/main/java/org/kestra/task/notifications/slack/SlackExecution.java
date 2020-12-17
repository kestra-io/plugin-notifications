package org.kestra.task.notifications.slack;

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
import org.kestra.core.utils.UriProvider;

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send a slack message with execution information",
    description = "Main execution information are provided in the sent message (id, namespace, flow, state, duration, start date ...)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a slack notification on failed flow",
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
                "      - id: slack",
                "        type: org.kestra.task.notifications.slack.SlackExecution",
                "        url: \"https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX\"",
                "        channel: \"#random\"",
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
public class SlackExecution extends SlackTemplate {

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        @SuppressWarnings("unchecked")
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        this.templateUri = "slack-template.hbs";

        this.templateRenderMap = new HashMap<>();
        this.templateRenderMap.put("duration", execution.getState().humanDuration());
        this.templateRenderMap.put("startDate", execution.getState().getStartDate());

        UriProvider uriProvider = runContext.getApplicationContext().getBean(UriProvider.class);
        this.templateRenderMap.put("link", uriProvider.executionUrl(execution));

        execution
            .findFirstByState(State.Type.FAILED)
            .ifPresentOrElse(
                taskRun -> this.templateRenderMap.put("firstFailed", taskRun),
                () -> this.templateRenderMap.put("firstFailed", false)
            );

        return super.run(runContext);
    }
}
