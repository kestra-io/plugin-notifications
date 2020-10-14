package org.kestra.task.notifications.slack;

import com.google.common.base.Charsets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;
import org.kestra.core.serializers.JacksonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Task to send a slack message with execution information",
    body = "Main execution information are provided in the sent message (id, namespace, flow, state, duration, start date ...)."
)
@Example(
    title = "Send a slack notification on failed flow",
    full = true,
    code = {
        "id: mail",
        "namespace: org.kestra.tests",
        "",
        "listeners:",
        "  - conditions:",
        "      - type: org.kestra.core.models.conditions.types.ExecutionStatusCondition",
        "        in:",
        "          - FAILED",
        "    tasks:",
        "      - id: slack",
        "        type: org.kestra.task.notifications.slack.SlackExecution",
        "        url: \"https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX\"",
        "        channel: \"#random\"",
        "",
        "",
        "tasks:",
        "  - id: ok",
        "    type: org.kestra.core.tasks.debugs.Return",
        "    format: \"{{task.id}} > {{taskrun.startDate}}\""
    }
)
public class SlackExecution extends SlackIncomingWebhook {
    @InputProperty(
        description = "Slack channel to send the message to"
    )
    private String channel;

    @InputProperty(
        description = "Author of the slack message"
    )
    private String username;

    @InputProperty(
        description = "Url of the icon to use"
    )
    private String iconUrl;

    @InputProperty(
        description = "Emoji icon to use"
    )
    private String iconEmoji;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
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
