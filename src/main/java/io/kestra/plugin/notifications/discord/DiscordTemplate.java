package io.kestra.plugin.notifications.discord;

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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class DiscordTemplate extends DiscordIncomingWebhook {

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
        title = "Webhook username"
    )
    @PluginProperty(dynamic = true)
    protected String username;

    @Schema(
        title = "Webhook profile photo URL"
    )
    @PluginProperty(dynamic = true)
    protected String avatarUrl;

    @Schema(
        title = "Title"
    )
    @PluginProperty(dynamic = true)
    protected String title;

    @Schema(
        title = "Message description"
    )
    @PluginProperty(dynamic = true)
    protected String description;

    @Schema(
        title = "Thumbnail URL"
    )
    @PluginProperty(dynamic = true)
    protected String thumbnail;

    @Schema(
        title = "Message author name"
    )
    @PluginProperty(dynamic = true)
    protected String authorName;

    @Schema(
        title = "Message content"
    )
    @PluginProperty(dynamic = true)
    protected String content;

    @Schema(
        title = "Footer text"
    )
    @PluginProperty(dynamic = true)
    protected String footer;

    @Schema(
        title = "RGB color of text"
    )
    @PluginProperty
    protected Integer color;

    @Schema(
        title = "Website URL"
    )
    @PluginProperty(dynamic = true)
    protected String websiteUrl;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, Object> mainMap = new HashMap<>();
        Map<String, Object> embedMap = new HashMap<>();

        if (this.templateUri != null) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );

            String render = runContext.render(template, templateRenderMap != null ? templateRenderMap : Map.of());
            mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        if (this.username != null) {
            mainMap.put("username", runContext.render(this.username));
        }

        if (this.avatarUrl != null) {
            mainMap.put("avatar_url", runContext.render(this.avatarUrl));
        }

        if (this.title != null) {
            embedMap.put("title", runContext.render(this.title));
        }

        if (this.description != null) {
            embedMap.put("description", runContext.render(this.description));
        }

        if (this.url != null) {
            embedMap.put("url", runContext.render(this.url));
        }

        if (this.color != null) {
            embedMap.put("color", this.color);
        }

        if (this.footer != null) {
            Map<String, String> footerMap = Map.of("text", runContext.render(this.footer));
            embedMap.put("footer", footerMap);
        }

        if (this.thumbnail != null) {
            Map<String, String> thumbnailMap = Map.of("url", runContext.render(this.thumbnail));
            embedMap.put("thumbnail", thumbnailMap);
        }

        if (this.authorName != null) {
            Map<String, String> authorMap = Map.of(
                "name", runContext.render(authorName),
                "url", runContext.render(websiteUrl),
                "icon_url", runContext.render(this.avatarUrl)
                                                  );
            embedMap.put("author", authorMap);
        }

        mainMap.put("tts", false);
        mainMap.put("embeds", embedMap);

        if (this.content != null) {
            mainMap.put("content", runContext.render(this.content));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(mainMap);

        return super.run(runContext);
    }

}
