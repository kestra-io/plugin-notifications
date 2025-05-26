package io.kestra.plugin.notifications.discord;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
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
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(
        title = "Webhook username"
    )
    protected Property<String> username;

    @Schema(
        title = "Webhook avatar URL"
    )
    protected Property<String> avatarUrl;

    @Schema(
        title = "Adds an embed to the discord notification body"
    )
    @PluginProperty(dynamic = true)
    protected List<Embed> embedList;

    @Schema(
        title = "Message content"
    )
    protected Property<String> content;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final Optional<String> renderedPayload = runContext.render(this.payload).as(String.class);

        if (renderedPayload.isPresent() && !renderedPayload.get().isBlank()) {
            return super.run(runContext);
        }

        Map<String, Object> mainMap = new HashMap<>();

        final Optional<String> renderedUri = runContext.render(this.templateUri).as(String.class);

        if (renderedUri.isPresent()) {
            String template = IOUtils.toString(
                Objects.requireNonNull(this.getClass()
                    .getClassLoader().
                    getResourceAsStream(renderedUri.get())
                ),
                StandardCharsets.UTF_8
            );

            String render = runContext.render(template, templateRenderMap != null ?
                runContext.render(templateRenderMap).asMap(String.class, Object.class) :
                Map.of());
            mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);
        }

        if (this.username != null) {
            mainMap.put("username", runContext.render(this.username).as(String.class).get());
        }

        if (this.avatarUrl != null) {
            mainMap.put("avatar_url", runContext.render(this.avatarUrl).as(String.class).get());
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
                    Map<String, Object> map = (Map<String, Object>) existingEmbeds.getFirst();
                    embeds.getFirst().putAll(map);
                }
            }

            mainMap.put("embeds", embeds);
        }

        if (this.content != null) {
            mainMap.put("content", runContext.render(this.content).as(String.class).get());
        }

        this.payload = Property.ofValue(JacksonMapper.ofJson().writeValueAsString(mainMap));

        return super.run(runContext);
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Embed {

        @Schema(
            title = "Title"
        )
        protected Property<String> title;

        @Schema(
            title = "Website URL, link title with given URL"
        )
        protected Property<String> websiteUrl;

        @Schema(
            title = "Message description"
        )
        protected Property<String> description;

        @Schema(
            title = "Thumbnail URL"
        )
        protected Property<String> thumbnail;

        @Schema(
            title = "Message author name"
        )
        protected Property<String> authorName;

        @Schema(
            title = "RGB color of text",
            description = "Example: [255, 255, 255]"
        )
        @PluginProperty
        protected Integer[] color;

        @Schema(
            title = "Footer text"
        )
        protected Property<String> footer;

        private int getColor() {
            if (color.length >= 3) {
                int rgb = color[0];
                rgb = (rgb << 8) + color[1];
                rgb = (rgb << 8) + color[2];

                return rgb;
            }
            return 0;
        }

        public Map<String, Object> getEmbedMap(RunContext runContext, Property<String> avatarUrl) throws Exception {
            Map<String, Object> embedMap = new HashMap<>();

            runContext.render(this.title).as(String.class).ifPresent(t -> embedMap.put("title", t));

            runContext.render(this.description).as(String.class).ifPresent(d -> embedMap.put("description", d));

            runContext.render(this.websiteUrl).as(String.class).ifPresent(url -> embedMap.put("url", url));

            runContext.render(this.thumbnail).as(String.class).ifPresent(url -> embedMap.put("thumbnail", Map.of("url", url)));

            if (this.authorName != null) {
                embedMap.put("author", Map.of(
                    "name", runContext.render(this.authorName).as(String.class).orElse(null),
                    "url", runContext.render(this.websiteUrl).as(String.class).orElse(null),
                    "icon_url", runContext.render(avatarUrl).as(String.class).orElse(null)
                ));
            }

            if (this.color != null) {
                embedMap.put("color", getColor());
            }

            runContext.render(this.footer).as(String.class).ifPresent(t -> embedMap.put("footer", Map.of("text", t)));

            return embedMap;
        }

    }

}
