package io.kestra.plugin.notifications.slack;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import java.net.URL;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Generic task to send a slack message.",
    description = "See <a href=\"https://api.slack.com/messaging/webhooks\">Sending messages using Incoming Webhooks</a>"
)
public class SlackIncomingWebhook extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Slack incoming webhook url",
        description = "See <a href=\"https://api.slack.com/messaging/webhooks#create_a_webhook\">Create an Incoming Webhook</a> "
    )
    @PluginProperty(dynamic = true)
    private String url;

    @Schema(
        title = "Slack message payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (RxHttpClient client = new DefaultHttpClient(new URL(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send slack webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
