package io.kestra.plugin.notifications.zenduty;

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

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class ZendutyTemplate extends ZendutyAlert {

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
        title = "Event title"
    )
    @PluginProperty(dynamic = true)
    protected String message;

    @Schema(
        title = "Event message. Summary description"
    )
    @PluginProperty(dynamic = true)
    protected String summary;

    @Schema(
        title = "Event alert type",
        implementation = AlertType.class
    )
    @PluginProperty
    protected AlertType alertType;

    @Schema(
        title = "A unique id for the alert. If not provided, the Zenduty API will create one"
    )
    @PluginProperty(dynamic = true)
    protected String entityId;

    @Schema(
        title = "List of URLs related to the alert"
    )
    @PluginProperty(dynamic = true)
    protected List<String> urls;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );

            String render = runContext.render(template, templateRenderMap != null ? templateRenderMap : Map.of());
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        if (this.message != null) {
            map.put("message", runContext.render(this.message));
        }

        if (this.summary != null) {
            map.put("summary", runContext.render(this.summary));
        }

        if (this.alertType != null) {
            map.put("alert_type", runContext.render(this.alertType.name().toLowerCase()));
        }

        if (this.entityId != null) {
            map.put("entity_id", runContext.render(this.entityId));
        }

        if (this.urls != null) {
            map.put("urls", runContext.render(this.urls));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
