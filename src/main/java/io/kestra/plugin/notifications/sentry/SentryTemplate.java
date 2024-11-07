package io.kestra.plugin.notifications.sentry;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class SentryTemplate extends SentryAlert {

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
        title = "Hexadecimal string representing a uuid4 value. The length is exactly 32 characters. Dashes are not allowed. It has to be lowercase.",
        defaultValue = "a generated unique identifier"
    )
    @Pattern(regexp = "[0-9a-f]{8}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{12}")
    @NotNull
    @Builder.Default
    @PluginProperty(dynamic = true)
    protected String eventId = UUID.randomUUID().toString().toLowerCase().replace("-", "");

    @Schema(
        title = "A string representing the platform the SDK is submitting from. This will be used by the Sentry interface to customize various components."
    )
    @NotNull
    @Builder.Default
    protected Property<Platform> platform = Property.of(Platform.JAVA);

    @Schema(
        title = "The record severity",
        description = "Acceptable values are: `fatal`, `error`, `warning`, `info`, `debug`."
    )
    @Builder.Default
    protected Property<ErrorLevel> level = Property.of(ErrorLevel.ERROR);

    @Schema(
        title = "The name of the transaction which caused this alert",
        description = "For example, in a web app, this might be the route name"
    )
    protected Property<String> transaction;

    @Schema(
        title = "Identifies the host from which the event was recorded"
    )
    protected Property<String> serverName;

    @Schema(
        title = "An arbitrary mapping of additional metadata to store with the event"
    )
    protected Property<Map<String, Object>> extra;

    @Schema(
        title = "An arbitrary mapping of additional metadata to store with the event"
    )
    protected Property<Map<String, Object>> errors;

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

        map.put("event_id", eventId);
        map.put("timestamp", Instant.now().toString());
        map.put("platform", runContext.render(platform).as(Platform.class).get().name().toLowerCase());

        if (runContext.render(this.level).as(ErrorLevel.class).isPresent()) {
            map.put("level", runContext.render(this.level).as(ErrorLevel.class).get().name().toLowerCase());
        }

        if (runContext.render(this.transaction).as(String.class).isPresent()) {
            map.put("transaction", runContext.render(this.transaction).as(String.class).get());
        }

        if (runContext.render(this.serverName).as(String.class).isPresent()) {
            map.put("server_name", runContext.render(this.serverName).as(String.class).get());
        }

        final Map<String, Object> renderedExtraMap = runContext.render(extra).asMap(String.class, Object.class);
        if (!renderedExtraMap.isEmpty()) {
            Map<String, Object> extra = (Map) map.getOrDefault("extra", new HashMap<>());
            extra.putAll(renderedExtraMap);
            map.put("extra", extra);
        }

        final Map<String, Object> renderedErrorsMap = runContext.render(this.errors).asMap(String.class, Object.class);
        if (!renderedErrorsMap.isEmpty()) {
            map.put("errors", renderedErrorsMap);
        }

        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }

}
