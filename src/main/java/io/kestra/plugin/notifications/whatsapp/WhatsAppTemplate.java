package io.kestra.plugin.notifications.whatsapp;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class WhatsAppTemplate extends WhatsAppIncomingWebhook {

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
        title = "Sender profile name"
    )
    @PluginProperty(dynamic = true)
    protected String profileName;

    @Schema(
        title = "The WhatsApp ID of the contact"
    )
    @PluginProperty
    protected List<String> whatsAppIds;

    @Schema(
        title = "WhatsApp ID of the sender (Phone number)"
    )
    @PluginProperty(dynamic = true)
    protected String from;

    @Schema(
        title = "Message id"
    )
    @PluginProperty(dynamic = true)
    protected String messageId;

    @Schema(
        title = "Message"
    )
    @PluginProperty(dynamic = true)
    protected String textBody;


    @Schema(
        title = "WhatsApp recipient ID"
    )
    @PluginProperty(dynamic = true)
    protected String recipientId;

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

        if (this.profileName != null && this.whatsAppIds != null && !this.whatsAppIds.isEmpty()) {
            List<Map<String, Object>> profiles = this.whatsAppIds.stream()
                .map(throwFunction(WhatsAppId -> Map.of("profile", Map.of("name", runContext.render(this.profileName)), "wa_id", WhatsAppId)))
                .toList();

            map.put("contacts", profiles);
        }

        if (this.from != null) {
            Map<String, Object> message = new HashMap<>(Map.of("from", runContext.render(this.from)));

            if (messageId != null) {
                message.put("id", runContext.render(this.messageId));
            }

            if (textBody != null) {
                message.put("text", Map.of("body", runContext.render(this.textBody)));
            } else {
                message.put("text", ((List<Map<String, Object>>)map.get("messages")).get(0).getOrDefault("text", ""));
            }

            message.put("type", "text");

            map.put("messages", List.of(message));
        }

        if (recipientId != null) {
            map.put("recipient_id", runContext.render(recipientId));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }

}
