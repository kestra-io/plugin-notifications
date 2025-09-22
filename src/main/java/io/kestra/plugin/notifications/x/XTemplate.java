package io.kestra.plugin.notifications.x;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class XTemplate extends XIncomingWebhook {

    @Schema(
        title = "Template to use",
        hidden = true
    )
    protected Property<String> templateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.message = Property.ofValue(buildTweetMessage(runContext));

        return super.run(runContext);
    }

    private String buildTweetMessage(RunContext runContext) {
        try {
            String customMessage = runContext.render(this.message).as(String.class).orElse(null);
            if (customMessage != null) {
                return customMessage;
            }

            if (templateUri != null) {
                final var renderedTemplateUri = runContext.render(templateUri).as(String.class);
                if (renderedTemplateUri.isPresent()) {
                    String template = IOUtils.toString(
                        Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(renderedTemplateUri.get())),
                        StandardCharsets.UTF_8
                    );

                    Map<String, Object> renderMap = templateRenderMap != null ?
                        runContext.render(templateRenderMap).asMap(String.class, Object.class) :
                        Map.of();

                    return runContext.render(template, renderMap);
                }
            }

            return "Tweet from Kestra";
        } catch (Exception e) {
            throw new RuntimeException("Failed to build tweet message", e);
        }
    }
}
