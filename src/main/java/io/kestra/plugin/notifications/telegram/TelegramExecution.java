package io.kestra.plugin.notifications.telegram;

import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.ExecutionInterface;
import io.kestra.plugin.notifications.services.ExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
        title = "Send input as a telegram message"
)
public class TelegramExecution extends TelegramTemplate implements ExecutionInterface {

    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        this.templateUri = "telegram-template.peb";
        logger.debug("Execution {}", this);
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);
        logger.debug("Execution {}", this);

        return super.run(runContext);
    }
}
