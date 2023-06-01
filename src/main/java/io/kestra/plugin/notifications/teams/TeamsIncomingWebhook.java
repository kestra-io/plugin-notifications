package io.kestra.plugin.notifications.teams;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send a Microsoft Teams notification message to an incoming webhook.",
    description = "See <a href=\"https://learn.microsoft.com/en-us/azure/data-factory/how-to-send-notifications-to-teams?tabs=data-factory\">Send notifications to a Microsoft Teams channel</a>."
)

@Plugin(
    examples = {
        @Example(
            title = "Send a Microsoft Teams notification message",
            code = {
                "url: \"https://microsoft.webhook.office.com/webhookb2/XXXXXXXXXX\"",
                "payload: |",
                "  {",
                "    \"@type\": \"MessageCard\",",
                "    \"@context\": \"http://schema.org/extensions\",",
                "    \"themeColor\": \"0076D7\",",
                "    \"summary\": \"Notification message\",",
                "    \"sections\": [{",
                "      \"activityTitle\": \"Rolling Workflow started\",",
                "      \"activitySubtitle\": \"Workflow Notification\",",
                "      \"markdown\": true",
                "    }],",
                "    \"potentialAction\": [",
                "      {",
                "        \"@type\": \"OpenUri\",",
                "        \"name\": \"Rolling Workflow\",",
                "        \"targets\": [",
                "          {",
                "           \"os\": \"default\",",
                "           \"uri\": \"{{ vars.systemUrl }}\"",
                "          }",
                "        ]",
                "      }",
                "    ]",
                "  }"
            }
        )
    }
)
public class TeamsIncomingWebhook  extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Microsoft Teams incoming webhook URL"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    private String url;

    @Schema(
        title = "Microsoft Teams message payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload);

            runContext.logger().debug("Send Microsoft Teams webhook: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
