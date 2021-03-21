package io.kestra.plugin.notifications.mail;

import com.google.common.base.Charsets;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send a mail using provided template information"
)
public class MailTemplate extends MailSend {
    @Schema(
        title = "Template to use"
    )
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
        title = "Render map to use for template"
    )
    @PluginProperty(dynamic = true)
    protected Map<String, Object> templateRenderMap;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {

        Map<String, Object> map = new HashMap<>();
        String htmlTextTemplate = "";

        if (this.templateUri != null) {
            htmlTextTemplate = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                Charsets.UTF_8
            );
        }

        this.htmlTextContent = runContext.render(htmlTextTemplate, templateRenderMap != null ? templateRenderMap : Map.of());

        return super.run(runContext);
    }
}
