package io.kestra.plugin.notifications.whatsapp;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwFunction;

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
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(
        title = "Sender profile name"
    )
    protected Property<String> profileName;

    @Schema(
        title = "The WhatsApp ID of the contact"
    )
    protected Property<List<String>> whatsAppIds;

    @Schema(
        title = "WhatsApp ID of the sender (Phone number)"
    )
    protected Property<String> from;

    @Schema(
        title = "Message id"
    )
    protected Property<String> messageId;

    @Schema(
        title = "Message"
    )
    protected Property<String> textBody;


    @Schema(
        title = "WhatsApp recipient ID"
    )
    protected Property<String> recipientId;

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

        final var renderedProfileName = runContext.render(this.profileName).as(String.class);
        final var renderedWhatsAppIds = runContext.render(this.whatsAppIds).asList(String.class);
        if (renderedProfileName.isPresent() && !renderedWhatsAppIds.isEmpty()) {
            List<Map<String, Object>> profiles = renderedWhatsAppIds.stream()
                .map(throwFunction(WhatsAppId -> Map.of(
                    "profile", Map.of("name", renderedProfileName.get()),
                    "wa_id", WhatsAppId)))
                .toList();

            map.put("contacts", profiles);
        }

        final var renderedFrom = runContext.render(this.from).as(String.class);
        if (renderedFrom.isPresent()) {
            Map<String, Object> message = new HashMap<>(Map.of("from", renderedFrom.get()));

            runContext.render(this.messageId).as(String.class).ifPresent(id -> message.put("id", id));


            if (runContext.render(this.textBody).as(String.class).isPresent()) {
                message.put("text", Map.of("body", runContext.render(this.textBody).as(String.class).get()));
            } else {
                message.put("text", ((List<Map<String, Object>>)map.get("messages")).getFirst().getOrDefault("text", ""));
            }

            message.put("type", "text");

            map.put("messages", List.of(message));
        }

        if (runContext.render(recipientId).as(String.class).isPresent()) {
            map.put("recipient_id", runContext.render(recipientId).as(String.class).get());
        }

        this.payload = Property.ofValue(JacksonMapper.ofJson().writeValueAsString(map));

        return super.run(runContext);
    }

}
