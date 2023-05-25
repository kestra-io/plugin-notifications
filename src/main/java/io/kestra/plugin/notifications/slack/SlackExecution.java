package io.kestra.plugin.notifications.slack;

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
    title = "Task to send a Slack message with the execution information",
    description = "Main execution information is provided in the sent message (id, namespace, flow, state, duration, start date, ...)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Slack notification on failed flow",
            full = true,
            code = {
                "id: slack",
                "namespace: io.kestra.tests",
                "",
                "listeners:",
                "  - conditions:",
                "      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "        in:",
                "          - FAILED",
                "    tasks:",
                "      - id: slack",
                "        type: io.kestra.plugin.notifications.slack.SlackExecution",
                "        url: \"https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX\"",
                "        channel: \"#random\"",
                "",
                "tasks:",
                "  - id: always fail",
                "    type: io.kestra.core.tasks.executions.Fail"
            }
        )
    }
)
public class SlackExecution extends SlackTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "slack-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
