package io.kestra.plugin.notifications.opsgenie;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class OpsgenieTemplate extends OpsgenieAlert {

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


    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected String message;


    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected String alias;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, String> responders;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, String> visibleTo;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected List<String> tags;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected String priority;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );

            String render = runContext.render(template, templateRenderMap != null ? runContext.render(templateRenderMap) : Map.of());
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        if (this.message != null) {
            map.put("message", runContext.render(message));
        }

        if (this.alias != null) {
            map.put("alias", runContext.render(alias));
        }

        if (this.responders != null) {
            List<Map<String, String>> respondersList = responders.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "type", entry.getValue()))
                .toList();

            map.put("responders", respondersList);
        }

        if (this.visibleTo != null) {
            List<Map<String, String>> visibleToList = visibleTo.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "type", entry.getValue()))
                .toList();
            map.put("visibleTo", visibleToList);
        }

        if (this.tags != null) {
            map.put("tags", tags);
        }

        if (this.priority != null) {
            map.put("priority", runContext.render(priority));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
