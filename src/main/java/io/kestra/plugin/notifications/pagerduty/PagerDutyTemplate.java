package io.kestra.plugin.notifications.pagerduty;

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

import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class PagerDutyTemplate extends PagerDutyAlert {

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
        title = "Integration Key for an integration on a PagerDuty service"
    )
    @PluginProperty(dynamic = true)
    protected String routingKey;

    @Schema(
        title = "Deduplication key for correlating triggers and resolves"
    )
    @PluginProperty(dynamic = true)
    protected String deduplicationKey;

    @Schema(
        title = "The type of event. Can be trigger, acknowledge or resolve."
    )
    @PluginProperty(dynamic = true)
    protected String eventAction;

    @Schema(
        title = "Brief text summary of the event, used to generate the summaries/titles of any associated alerts"
    )
    @Size(max = 1024)
    @PluginProperty(dynamic = true)
    protected String payloadSummary;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );

            String render = runContext.render(template, templateRenderMap != null ? templateRenderMap : Map.of());
            map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
            payload = (Map<String, Object>) map.getOrDefault("payload", new HashMap<>());
        }

        if (this.routingKey != null) {
            map.put("routing_key", runContext.render(routingKey));
        }

        if (this.deduplicationKey != null) {
            map.put("dedup_key", runContext.render(deduplicationKey));
        }

        if (this.eventAction != null) {
            map.put("event_action", runContext.render(eventAction));
        }

        if (this.payloadSummary != null) {
            payload.put("summary", runContext.render(payloadSummary));
        }

        payload.put("timestamp", LocalDateTime.now());

        map.replace("payload", payload);

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
