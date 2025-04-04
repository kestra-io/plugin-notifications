package io.kestra.plugin.notifications.pagerduty;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(
        title = "Integration Key for an integration on a PagerDuty service"
    )
    protected Property<String> routingKey;

    @Schema(
        title = "Deduplication key for correlating triggers and resolves"
    )
    @PluginProperty(dynamic = true)
    protected Property<String> deduplicationKey;

    @Schema(
        title = "The type of event. Can be trigger, acknowledge or resolve."
    )
    protected Property<String> eventAction;

    @Schema(
        title = "Brief text summary of the event, used to generate the summaries/titles of any associated alerts."
    )
    @Size(max = 1024)
    @PluginProperty(dynamic = true)
    protected String payloadSummary;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();

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
            payload = (Map<String, Object>) map.getOrDefault("payload", new HashMap<>());
        }

        if (runContext.render(routingKey).as(String.class).isPresent()) {
            map.put("routing_key", runContext.render(routingKey).as(String.class).get());
        }

        if (runContext.render(deduplicationKey).as(String.class).isPresent()) {
            map.put("dedup_key", runContext.render(deduplicationKey).as(String.class).get());
        }

        if (runContext.render(eventAction).as(String.class).isPresent()) {
            map.put("event_action", runContext.render(eventAction).as(String.class).get());
        }

        if (this.payloadSummary != null) {
            payload.put("summary", runContext.render(payloadSummary));
        }

        payload.put("timestamp", LocalDateTime.now());

        map.replace("payload", payload);

        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }

}
