package io.kestra.plugin.notifications.zulip;

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
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class ZulipTemplate extends ZulipIncomingWebhook {
    @Schema(
        title = "Zulip channel to send the message to"
    )
    protected Property<String> channel;

    @Schema(
        title = "Author of the zulip message"
    )
    protected Property<String> username;

    @Schema(
        title = "Url of the icon to use"
    )
    protected Property<String> iconUrl;

    @Schema(
        title = "Emoji icon to use"
    )
    protected Property<String> iconEmoji;

    @Schema(
        title = "Template to use",
        hidden = true
    )
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;


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

        if (runContext.render(this.channel).as(String.class).isPresent()) {
            map.put("channel", runContext.render(this.channel).as(String.class).get());
        }

        if (runContext.render(this.username).as(String.class).isPresent()) {
            map.put("username", runContext.render(this.username).as(String.class).get());
        }

        if (runContext.render(this.iconUrl).as(String.class).isPresent()) {
            map.put("icon_url", runContext.render(this.iconUrl).as(String.class).get());
        }

        if (runContext.render(this.iconEmoji).as(String.class).isPresent()) {
            map.put("icon_emoji", runContext.render(this.iconEmoji).as(String.class).get());
        }

        this.payload = Property.ofValue(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }
}
