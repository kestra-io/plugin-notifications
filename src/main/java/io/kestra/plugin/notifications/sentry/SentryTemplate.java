package io.kestra.plugin.notifications.sentry;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
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
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> templateRenderMap;

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
    @PluginProperty(dynamic = true)
    protected Platform platform = Platform.JAVA;

    @Schema(
        title = "The record severity",
        description = "Acceptable values are: `fatal`, `error`, `warning`, `info`, `debug`."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    protected ErrorLevel level = ErrorLevel.ERROR;

    @Schema(
        title = "The name of the transaction which caused this alert",
        description = "For example, in a web app, this might be the route name"
    )
    @PluginProperty(dynamic = true)
    protected String transaction;

    @Schema(
        title = "Identifies the host from which the event was recorded"
    )
    @PluginProperty(dynamic = true)
    protected String serverName;

    @Schema(
        title = "An arbitrary mapping of additional metadata to store with the event"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> extra;

    @Schema(
        title = "An arbitrary mapping of additional metadata to store with the event"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> errors;

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

        map.put("event_id", eventId);
        map.put("timestamp", Instant.now().toString());
        map.put("platform", platform.name().toLowerCase());

        if (this.level != null) {
            map.put("level", runContext.render(this.level.name().toLowerCase()));
        }

        if (this.transaction != null) {
            map.put("transaction", runContext.render(this.transaction));
        }

        if (this.serverName != null) {
            map.put("server_name", runContext.render(this.serverName));
        }

        if (this.extra != null) {
            Map<String, Object> extra = (Map) map.getOrDefault("extra", new HashMap<>());
            extra.putAll(runContext.render(this.extra));
            map.put("extra", extra);
        }

        if (this.errors != null) {
            map.put("errors", runContext.render(this.errors));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
