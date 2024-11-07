package io.kestra.plugin.notifications.sendgrid;

import io.kestra.core.models.property.Property;
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
public abstract class SendGridMailTemplate extends SendGridMailSend {
    @Schema(
        title = "Template to use",
        hidden = true
    )
    protected Property<String> templateUri;

    @Schema(
        title = "Text template to use",
        hidden = true
    )
    protected Property<String> textTemplateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    protected Property<Map<String, Object>> templateRenderMap;

    @Override
    public SendGridMailSend.Output run(RunContext runContext) throws Exception {
        String plainTextTemplate = "";
        String htmlTextTemplate = "";

        final var renderedTemplateUri = runContext.render(this.templateUri).as(String.class);
        if (renderedTemplateUri.isPresent()) {
            htmlTextTemplate = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(renderedTemplateUri.get())),
                StandardCharsets.UTF_8
            );
        }

        if (runContext.render(this.textTemplateUri).as(String.class).isPresent()) {
            plainTextTemplate = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(runContext.render(this.textTemplateUri).as(String.class).get())),
                StandardCharsets.UTF_8
            );
        }

        this.htmlContent = Property.of(runContext.render(htmlTextTemplate, runContext.render(templateRenderMap).asMap(String.class, Object.class)));
        this.textContent = Property.of(runContext.render(plainTextTemplate, runContext.render(templateRenderMap).asMap(String.class, Object.class)));

        return super.run(runContext);
    }
}
