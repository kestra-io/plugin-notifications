package io.kestra.plugin.notifications.teams;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.ExecutionInterface;
import io.kestra.plugin.notifications.services.ExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send a Microsoft Teams message with the execution information",
    description = "Main execution information is provided in the sent message (id, namespace, flow, state, duration, start date, ...)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Microsoft Teams notification on failed flow",
            full = true,
            code = {
                "id: teams",
                "namespace: io.kestra.tests",
                "",
                "listeners:",
                "  - conditions:",
                "      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "        in:",
                "          - FAILED",
                "    tasks:",
                "      - id: teams",
                "        type: io.kestra.plugin.notifications.teams.TeamsExecution",
                "        url: \"https://microsoft.webhook.office.com/webhookb2/XXXXXXXXXX\"",
                "        activityTitle: \"Kestra Teams notification\"",
                "",
                "tasks:",
                "  - id: alwaysFail",
                "    type: io.kestra.core.tasks.executions.Fail"
            }
        )
    }
)
public class TeamsExecution extends TeamsTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "teams-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
