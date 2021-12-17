package io.kestra.plugin.notifications.slack;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.micronaut.http.HttpRequest;
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

import java.net.URI;
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

@Plugin(
    examples = {
        @Example(
            title = "Send a slack notification",
            code = {
                "url: \"https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX\"",
                "payload: |",
                "  {",
                "    \"channel\": \"#my-chan\",",
                "    \"text\": \"Flow `{{ flow.namespace }}.{{ flow.id }}` started with execution `{{ execution.id }}`\"",
                "  }"
            }
        )
    }
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

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send slack webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
