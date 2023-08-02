package io.kestra.plugin.notifications.telegram;

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
        title = "Send input as a telegram message"
)
@Plugin(
        examples = {
                @Example(
                        title = "Send a Telegram notification on failed flow",
                        full = true,
                        code = {
                                "id: telegram",
                                "namespace: io.kestra.tests",
                                "",
                                "listeners:",
                                "  - conditions:",
                                "      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                                "        in:",
                                "          - FAILED",
                                "    tasks:",
                                "      - id: telegram",
                                "        type: io.kestra.plugin.notifications.telegram.TelegramExecution",
                                "        channel: \"2072728690\"",
                                "        token: \"6090305634:AAHHlR1R4H0JOZBFJlPp9jbSFrDwYwaS\",",
                                "",
                                "tasks:",
                                "  - id: alwaysFail",
                                "    type: io.kestra.core.tasks.executions.Fail"
                        }
                )
        }
)
public class TelegramExecution extends TelegramTemplate implements ExecutionInterface {

    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "telegram-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);
        return super.run(runContext);
    }
}
