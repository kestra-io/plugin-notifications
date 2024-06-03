package io.kestra.plugin.notifications.sendgrid;

import com.google.common.base.Charsets;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

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
    public SendGridMailSend.Output run(RunContext runContext) throws Exception {
        String plainTextTemplate = "";
        String htmlTextTemplate = "";

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

        this.htmlContent = runContext.render(htmlTextTemplate, MapUtils.emptyOnNull(templateRenderMap));
        this.textContent = runContext.render(plainTextTemplate, MapUtils.emptyOnNull(templateRenderMap));

        return super.run(runContext);
    }
}
