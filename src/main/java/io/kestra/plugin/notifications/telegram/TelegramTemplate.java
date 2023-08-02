package io.kestra.plugin.notifications.telegram;

import com.google.common.base.Charsets;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class TelegramTemplate extends TelegramSend {

    @Schema(
            title = "Template to use",
            hidden = true
    )
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
            title = "Map of variables to use for the message template (Unused in the default template)"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> templateRenderMap;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {

        Map<String, Object> map = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                    Charsets.UTF_8
            );

            this.payload = runContext.render(template, templateRenderMap != null ? templateRenderMap : Map.of());
        }

        return super.run(runContext);
    }
}
