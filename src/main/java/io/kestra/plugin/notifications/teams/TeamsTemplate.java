package io.kestra.plugin.notifications.teams;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
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
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Theme color")
    @Builder.Default
    protected Property<String> themeColor = Property.ofValue("0076D7");

    @Schema(title = "Activity Title")
    protected Property<String> activityTitle;

    @Schema(title = "Activity Subtitle")
    protected Property<String> activitySubtitle;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();

        final var renderedTemplateUri = runContext.render(this.templateUri).as(String.class);
        if (renderedTemplateUri.isPresent()) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(renderedTemplateUri.get())),
                StandardCharsets.UTF_8
            );

            Map<String, Object> copy = new HashMap<>();
            final Map<String, Object> renderedTemplateRenderMap = runContext.render(templateRenderMap).asMap(String.class, Object.class);
            if (!renderedTemplateRenderMap.isEmpty()) {
                copy.putAll(renderedTemplateRenderMap);
            }

            runContext.render(themeColor).as(String.class).ifPresent(c -> copy.put("themeColor", c));
            runContext.render(this.activityTitle).as(String.class).ifPresent(c -> copy.put("activityTitle", c));
            runContext.render(this.activitySubtitle).as(String.class).ifPresent(c -> copy.put("activitySubtitle", c));

            String render = runContext.render(template, copy);
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        this.payload = Property.ofValue(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }
}
