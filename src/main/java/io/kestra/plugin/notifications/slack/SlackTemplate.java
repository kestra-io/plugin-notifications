package io.kestra.plugin.notifications.slack;

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
public abstract class SlackTemplate extends SlackIncomingWebhook {

    // DEPRECATED PROPERTIES //
    @Schema(
        title = "Slack channel to send the message to. [DEPRECATED] (For an incoming webhook the channel will be included in the URL.)"
    )
    @Deprecated(forRemoval = true, since = "0.21")
    protected Property<String> channel;

    @Schema(
        title = "URL of the icon to use. [DEPRECATED] (For an incoming webhook the icon will be always be the one from the bot/app.)"
    )
    @Deprecated(forRemoval = true, since = "0.21")
    protected Property<String> iconUrl;

    @Schema(
        title = "Emoji icon to use"
    )
    @Deprecated(forRemoval = true, since = "0.21")
    protected Property<String> iconEmoji;

    ///////////////////////////////////////////////////////////

    @Schema(
        title = "Author of the slack message"
    )
    protected Property<String> username;

    @Schema(
        title = "URL of the image to be used for the author icon."
    )
    protected Property<String> authorIconUrl;

    @Schema(
        title = "URL of the image to be put at the bottom in the attachments."
    )
    protected Property<String> imageUrl;

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

        //Use payload if exists
        var renderedPayload = runContext.render(this.payload).as(String.class);
        if (renderedPayload.isPresent()) {
            return super.run(runContext);
        }

        //Use template and override payload
        final var renderedTemplateUri = runContext.render(this.templateUri).as(String.class);
        final Map<String, Object> map = new HashMap<>();

        if (renderedTemplateUri.isPresent()) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(renderedTemplateUri.get())),
                StandardCharsets.UTF_8
            );

            Map<String, Object> templateMap = new HashMap<>(runContext.render(templateRenderMap).asMap(String.class, Object.class));

            runContext.render(this.username).as(String.class).ifPresent(name -> templateMap.put("username", name));
            runContext.render(this.authorIconUrl).as(String.class).ifPresent(name -> templateMap.put("author_icon", name));
            runContext.render(this.imageUrl).as(String.class).ifPresent(name -> templateMap.put("image_url", name));

            String render = runContext.render(template, templateMap);

            map.putAll((Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class));
        }

        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }
}
