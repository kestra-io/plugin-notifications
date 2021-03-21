package io.kestra.plugin.notifications.slack;

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
import io.kestra.core.serializers.JacksonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send a slack message using provided template information"
)
public class SlackTemplate extends SlackIncomingWebhook {
    @Schema(
        title = "Slack channel to send the message to"
    )
    @PluginProperty(dynamic = true)
    protected String channel;

    @Schema(
        title = "Author of the slack message"
    )
    @PluginProperty(dynamic = true)
    protected String username;

    @Schema(
        title = "Url of the icon to use"
    )
    @PluginProperty(dynamic = true)
    protected String iconUrl;

    @Schema(
        title = "Emoji icon to use"
    )
    @PluginProperty(dynamic = true)
    protected String iconEmoji;

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

        if (this.channel != null) {
            map.put("channel", runContext.render(this.channel));
        }

        if (this.username != null) {
            map.put("username", runContext.render(this.username));
        }

        if (this.iconUrl != null) {
            map.put("icon_url", runContext.render(this.iconUrl));
        }

        if (this.iconEmoji != null) {
            map.put("icon_emoji", runContext.render(this.iconEmoji));
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }
}
