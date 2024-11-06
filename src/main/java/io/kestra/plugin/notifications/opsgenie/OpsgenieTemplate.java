package io.kestra.plugin.notifications.opsgenie;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
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
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;


    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<String> message;


    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<String> alias;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, String>> responders;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, String>> visibleTo;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<List<String>> tags;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<String> priority;

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

            String render = runContext.render(template, templateRenderMap != null ?
                runContext.render(templateRenderMap).asMap(String.class, Object.class) :
                Map.of()
            );
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        if (runContext.render(message).as(String.class).isPresent()) {
            map.put("message", runContext.render(message).as(String.class).get());
        }

        if (runContext.render(alias).as(String.class).isPresent()) {
            map.put("alias", runContext.render(alias).as(String.class).get());
        }

        final Map<String, String> renderedResponders = runContext.render(this.responders).asMap(String.class, String.class);
        if (!renderedResponders.isEmpty()) {
            List<Map<String, String>> respondersList = renderedResponders.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "type", entry.getValue()))
                .toList();

            map.put("responders", respondersList);
        }

        final Map<String, String> renderedVisibleTo = runContext.render(this.visibleTo).asMap(String.class, String.class);
        if (!renderedVisibleTo.isEmpty()) {
            List<Map<String, String>> visibleToList = renderedVisibleTo.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "type", entry.getValue()))
                .toList();
            map.put("visibleTo", visibleToList);
        }

        final List<String> renderedTagList = runContext.render(tags).asList(String.class);
        if (!renderedTagList.isEmpty()) {
            map.put("tags", renderedTagList);
        }

        if (runContext.render(priority).as(String.class).isPresent()) {
            map.put("priority", runContext.render(priority).as(String.class).get());
        }

        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }

}
