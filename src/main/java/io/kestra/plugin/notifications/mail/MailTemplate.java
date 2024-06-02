package io.kestra.plugin.notifications.mail;

import com.google.common.base.Charsets;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.util.Map;
import java.util.Objects;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class MailTemplate extends MailSend {
    @Schema(
        title = "Template to use",
        hidden = true
    )
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
        title = "Text template to use",
        hidden = true
    )
    @PluginProperty(dynamic = true)
    protected String textTemplateUri;

    @Schema(
        title = "Map of variables to use for the message template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> templateRenderMap;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String htmlTextTemplate = EMPTY_STRING;
        String plainTextTemplate = EMPTY_STRING;

        if (this.templateUri != null) {
            htmlTextTemplate = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );
        }

        if (this.textTemplateUri != null) {
            plainTextTemplate = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.textTemplateUri)),
                Charsets.UTF_8
            );
        }

        this.htmlTextContent = runContext.render(htmlTextTemplate, templateRenderMap != null ? templateRenderMap : Map.of());
        this.plainTextContent = runContext.render(plainTextTemplate, templateRenderMap != null ? templateRenderMap : Map.of());

        return super.run(runContext);
    }
}
