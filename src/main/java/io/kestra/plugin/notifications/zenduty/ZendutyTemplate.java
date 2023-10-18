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

import java.util.HashMap;
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
        title = "Incident object's title"
    )
    @PluginProperty(dynamic = true)
    protected String title;

    @Schema(
        title = "Service object's unique_id"
    )
    @PluginProperty(dynamic = true)
    protected String service;

    @Schema(
        title = "Incident object's status. 1 is triggered, 2 is acknowledged and 3 is resolved. Default value is 1"
    )
    @PluginProperty
    protected Integer status;

    @Schema(
        title = "User object's username"
    )
    @PluginProperty(dynamic = true)
    protected String assignedTo;

    @Schema(
        title = "Escalation Policy object's unique_id"
    )
    @PluginProperty(dynamic = true)
    protected String escalationPolicy;

    @Schema(
        title = "SLA object's unique_id"
    )
    @PluginProperty(dynamic = true)
    protected String sla;

    @Schema(
        title = "Priority object's unique_id"
    )
    @PluginProperty(dynamic = true)
    protected String teamPriority;

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

        if (this.title != null) {
            map.put("title", runContext.render(this.title));
        }

        if (this.service != null) {
            map.put("service", runContext.render(this.service));
        }

        if (this.status != null) {
            map.put("status", this.status);
        }

        if (this.assignedTo != null) {
            map.put("assigned_to", runContext.render(this.assignedTo));
        }

        if (this.escalationPolicy != null) {
            map.put("escalation_policy", runContext.render(this.escalationPolicy));
        }

        if (this.sla != null) {
            map.put("sla", runContext.render(this.sla));
        }

        if (this.teamPriority != null) {
            map.put("team_priority", runContext.render(this.teamPriority));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
