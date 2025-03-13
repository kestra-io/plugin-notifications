package io.kestra.plugin.notifications.squadcast;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public abstract class SquadcastTemplate extends SquadcastIncomingWebhook {
    @Schema(
        title = "Incident message",
        description = "The main message of the incident."
    )
    @NotNull
    protected Property<String> message;

    @Schema(
        title = "Incident priority",
        description = "The priority of the incident (P1, P2, P3, P4, P5). Defaults to 'Unset' if invalid."
    )
    protected Property<String> priority;

    @Schema(
        title = "Unique event identifier associated with an incident"
    )
    protected Property<String> eventId;

    @Schema(
        title = "Incident status",
        description = "Status of the incident (e.g., 'trigger', 'resolve')."
    )
    protected Property<String> status;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, String>> tags;

    @Schema(
        title = "Template to use",
        hidden = true
    )
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

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

        if (runContext.render(this.message).as(String.class).isPresent()) {
            map.put("message", runContext.render(this.message).as(String.class).get());
        }

        if (runContext.render(this.priority).as(String.class).isPresent()) {
            map.put("priority", runContext.render(this.priority).as(String.class).get());
        }

        if (runContext.render(this.eventId).as(String.class).isPresent()) {
            map.put("event_id", runContext.render(this.eventId).as(String.class).get());
        }

        if (runContext.render(this.status).as(String.class).isPresent()) {
            map.put("status", runContext.render(this.status).as(String.class).get());
        }

        final Map<String, String> tags = runContext.render(this.tags).asMap(String.class, String.class);
        if (!tags.isEmpty()) {
            map.put("tags", tags);
        }

        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }
}