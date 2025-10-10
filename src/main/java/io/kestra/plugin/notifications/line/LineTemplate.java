package io.kestra.plugin.notifications.line;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a LINE broadcast message",
    description = "Send a broadcast message to all users who have added the LINE Official Account. Warning: limited to 60 requests per hour."
)
public abstract class LineTemplate extends AbstractHttpOptionsTask {

    @Schema(title = "LINE Messaging API URL", description = "The LINE API endpoint URL to broadcast a message to a channel")
    @Builder.Default
    protected Property<String> url = Property.ofValue("https://api.line.me/v2/bot/message/broadcast");

    @Schema(title = "Channel Access Token", description = "LINE Channel Access Token for authentication")
    @NotNull
    protected Property<String> channelAccessToken;

    @Schema(title = "Template to use", hidden = true)
    protected Property<String> templateUri;

    @Schema(title = "Map of variables to use for the message template")
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Message text body", description = "Direct message text (bypasses template)")
    protected Property<String> textBody;

    @Schema(title = "Custom fields", description = "Custom fields to include in the notification")
    protected Property<Map<String, Object>> customFields;

    @Schema(title = "Custom message", description = "Custom message to include in the notification")
    protected Property<String> customMessage;

    @Schema(title = "Execution ID", description = "The execution ID")
    @Builder.Default
    protected Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final var rChannelAccessToken = runContext.render(this.channelAccessToken).as(String.class)
            .orElseThrow();
        final var rUrl = runContext.render(this.url).as(String.class)
            .orElse("https://api.line.me/v2/bot/message/broadcast");

        String messageText = getMessageText(runContext);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("messages", List.of(Map.of(
                "type", "text",
                "text", messageText
            )));

            String payload = JacksonMapper.ofJson().writeValueAsString(messagePayload);

            runContext.logger().debug("Broadcasting LINE message: {}", payload);

            HttpRequest request = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + rChannelAccessToken)
                .uri(URI.create(rUrl))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder().content(payload).build())
                .build();

            HttpResponse<String> response = client.request(request, String.class);

            if (response.getStatus().getCode() == 200) {
                runContext.logger().info("LINE broadcast message sent successfully");
            } else {
                runContext.logger().error("Failed to send LINE broadcast message: {}", response.getBody());
            }
        }

        return null;
    }

    private String getMessageText(RunContext runContext) throws Exception {
        final var rTextBody = runContext.render(this.textBody).as(String.class);
        if (rTextBody.isPresent()) {
            return rTextBody.get();
        }

        final var rTemplateUri = runContext.render(this.templateUri).as(String.class);
        if (rTemplateUri.isPresent()) {
            String template = IOUtils.toString(
                Objects.requireNonNull(
                    this.getClass().getClassLoader().getResourceAsStream(rTemplateUri.get())),
                StandardCharsets.UTF_8);

            Map<String, Object> templateVars = templateRenderMap != null
                ? runContext.render(templateRenderMap).asMap(String.class, Object.class)
                : Map.of();

            return runContext.render(template, templateVars);
        }

        return "";
    }
}