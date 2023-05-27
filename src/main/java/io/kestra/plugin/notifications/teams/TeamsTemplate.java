package io.kestra.plugin.notifications.teams;

import com.google.common.base.Charsets;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class TeamsTemplate extends TeamsIncomingWebhook {
    @Schema(
        title = "Template to use",
        hidden = true
    )
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> templateRenderMap;

    @Schema(title = "Theme color")
    @Builder.Default
    @PluginProperty(dynamic = true)
    protected String themeColor = "0076D7";

    @Schema(title = "Activity Title")
    @PluginProperty(dynamic = true)
    protected String activityTitle;

    @Schema(title = "Activity Subtitle")
    @PluginProperty(dynamic = true)
    protected String activitySubtitle;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );

            Map<String, Object> copy = new HashMap<>();
            if (templateRenderMap != null) {
                copy.putAll(templateRenderMap);
            }
            copy.put("themeColor", runContext.render(themeColor));
            if (this.activityTitle != null) {
                copy.put("activityTitle", runContext.render(this.activityTitle));
            }
            if (this.activitySubtitle != null) {
                copy.put("activitySubtitle", runContext.render(this.activitySubtitle));
            }

            String render = runContext.render(template, copy);
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }
}
