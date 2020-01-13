package org.kestra.task.notifications.slack;

import com.google.common.base.Charsets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.serializers.JacksonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class SlackExecution extends SlackIncomingWebhook {
    private String channel;

    private String username;

    private String iconUrl;

    private String iconEmoji;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        @SuppressWarnings("unchecked")
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        String template = IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("slack-template.hbs")),
            Charsets.UTF_8
        );

        Map<String, Object> renderMap = new HashMap<>();
        renderMap.put("duration", execution.getState().humanDuration());
        renderMap.put("startDate", execution.getState().getStartDate());
        renderMap.put("link", "https://todo.com");

        execution
            .findFirstByState(State.Type.FAILED)
            .ifPresentOrElse(
                taskRun -> renderMap.put("firstFailed", taskRun),
                () -> renderMap.put("firstFailed", false)
            );

        String render = runContext.render(template, renderMap);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

        if (this.channel != null) {
            map.put("channel", this.channel);
        }

        if (this.username != null) {
            map.put("username", this.username);
        }

        if (this.iconUrl != null) {
            map.put("icon_url", this.iconUrl);
        }

        if (this.iconEmoji != null) {
            map.put("icon_emoji", this.iconEmoji);
        }

        this.payload = JacksonMapper.ofJson().writeValueAsString(map);

        return super.run(runContext);
    }
}