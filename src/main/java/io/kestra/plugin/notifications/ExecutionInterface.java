package io.kestra.plugin.notifications;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface ExecutionInterface {
    @Schema(
        title = "The execution id to use",
        description = "Default is the current execution"
    )
    @PluginProperty(dynamic = true)
    String getExecutionId();

    @Schema(
        title = "Custom fields to be added on notification"
    )
    @PluginProperty(dynamic = true)
    Map<String, Object> getCustomFields();

    @Schema(
        title = "Custom message to be added on notification"
    )
    @PluginProperty(dynamic = true)
    String getCustomMessage();
}
