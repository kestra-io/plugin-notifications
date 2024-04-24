package io.kestra.plugin.notifications.discord;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.util.*;

import static io.kestra.core.utils.Rethrow.throwFunction;

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
        title = "Webhook avatar URL"
    )
    @PluginProperty(dynamic = true)
    protected String avatarUrl;

    @Schema(
        title = "Adds an embed to the discord notification body"
    )
    @PluginProperty(dynamic = true)
    protected List<Embed> embedList;

    @Schema(
        title = "Message content"
    )
    @PluginProperty(dynamic = true)
    protected String content;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        if (payload != null && !payload.isBlank()) {
            return super.run(runContext);
        }

        Map<String, Object> mainMap = new HashMap<>();

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

        mainMap.put("tts", false);

        if (embedList != null) {
            List<Map<String, Object>> embeds = new ArrayList<>(
                embedList.stream()
                    .map(throwFunction(embedEntry -> embedEntry.getEmbedMap(runContext, avatarUrl)))
                    .toList()
            );

            if (mainMap.containsKey("embeds")) {
                List<Object> existingEmbeds = (List<Object>) mainMap.get("embeds");

                if (!existingEmbeds.isEmpty()) {
                    Map<String, Object> map = (Map<String, Object>) existingEmbeds.get(0);
                    embeds.get(0).putAll(map);
                }
            }

            mainMap.put("embeds", embeds);
        }

        if (this.content != null) {
            mainMap.put("content", runContext.render(this.content));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(mainMap);

        return super.run(runContext);
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Embed {

        @Schema(
            title = "Title"
        )
        @PluginProperty(dynamic = true)
        protected String title;

        @Schema(
            title = "Website URL, link title with given URL"
        )
        @PluginProperty(dynamic = true)
        protected String websiteUrl;

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
            title = "RGB color of text",
            description = "Example: [255, 255, 255]"
        )
        @PluginProperty
        protected Integer[] color;

        @Schema(
            title = "Footer text"
        )
        @PluginProperty(dynamic = true)
        protected String footer;

        private int getColor() {
            if (color.length >= 3) {
                int rgb = color[0];
                rgb = (rgb << 8) + color[1];
                rgb = (rgb << 8) + color[2];

                return rgb;
            }
            return 0;
        }

        public Map<String, Object> getEmbedMap(RunContext runContext, String avatarUrl) throws Exception {
            Map<String, Object> embedMap = new HashMap<>();

            if (this.title != null) {
                embedMap.put("title", runContext.render(this.title));
            }

            if (this.description != null) {
                embedMap.put("description", runContext.render(this.description));
            }

            if (this.websiteUrl != null) {
                embedMap.put("url", runContext.render(this.websiteUrl));
            }

            if (this.thumbnail != null) {
                Map<String, String> thumbnailMap = Map.of("url", runContext.render(this.thumbnail));
                embedMap.put("thumbnail", thumbnailMap);
            }

            if (this.authorName != null) {
                Map<String, String> authorMap = Map.of(
                    "name", runContext.render(this.authorName),
                    "url", runContext.render(this.websiteUrl),
                    "icon_url", runContext.render(avatarUrl)
                                                      );
                embedMap.put("author", authorMap);
            }

            if (this.color != null) {
                embedMap.put("color", getColor());
            }

            if (this.footer != null) {
                Map<String, String> footerMap = Map.of("text", runContext.render(this.footer));
                embedMap.put("footer", footerMap);
            }

            return embedMap;
        }

    }

}
