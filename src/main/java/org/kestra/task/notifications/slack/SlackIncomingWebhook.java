package org.kestra.task.notifications.slack;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.RxHttpClient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;

import java.net.URL;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Generic task to send a slack message.",
    body = "See <a href=\"https://api.slack.com/messaging/webhooks\">Sending messages using Incoming Webhooks</a>"
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
        "      - type: org.kestra.core.models.listeners.types.ExecutionStatusCondition",
        "        in:",
        "          - FAILED",
        "  - tasks:",
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
public class SlackIncomingWebhook extends Task implements RunnableTask<VoidOutput> {
    @InputProperty(
        description = "Slack incoming webhook url",
        body = "See <a href=\"https://api.slack.com/messaging/webhooks#create_a_webhook\">Create an Incoming Webhook</a> "
    )
    private String url;

    @InputProperty(
        description = "Slack message payload",
        dynamic = true
    )
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        try (RxHttpClient client = new DefaultHttpClient(new URL(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger(this.getClass()).debug("Send slack webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
