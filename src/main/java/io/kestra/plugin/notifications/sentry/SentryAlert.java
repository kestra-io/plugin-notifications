package io.kestra.plugin.notifications.sentry;

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

import javax.validation.constraints.NotBlank;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Sentry alert when a specific flow or task fails",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. \n\n The only required input is a DSN string value, which you can find when you go to your Sentry project settings and go to the section `Client Keys (DSN)`. You can find more detailed description of how to find your DSN in the [following Sentry documentation](https://docs.sentry.io/product/sentry-basics/concepts/dsn-explainer/#where-to-find-your-dsn). \n\n You can customize the alert `payload`, which is a JSON object, or you can skip it and use the default payload created by kestra. For more information about the payload, check the [Sentry Event Payloads documentation](https://develop.sentry.dev/sdk/event-payloads/). \n\n The `event_id` is an optional payload attribute that you can use to override the default event ID. If you don't specify it (recommended), kestra will generate a random UUID. You can use this attribute to group events together, but note that this must be a UUID type. For more information, check the [Sentry documentation](https://docs.sentry.io/product/issues/grouping-and-fingerprints/)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Sentry alert on a failed flow execution",
            full = true,
            code = """
                id: unreliable_flow
                namespace: prod

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}" # format: https://xxx@xxx.ingest.sentry.io/xxx"""
        ),
        @Example(
            title = "Send a custom Sentry alert",
            full = true,
            code = """
                id: sentry_alert
                namespace: dev

                tasks:
                  - id: send_sentry_message
                    type: io.kestra.plugin.notifications.sentry.SentryAlert
                    dsn: "{{ secret('SENTRY_DSN') }}"
                    payload: |
                      {
                          "timestamp": "{{ execution.startDate }}",
                          "platform": "java",
                          "level": "error",
                          "transaction": "/execution/id/{{ execution.id }}",
                          "server_name": "localhost:8080",
                          "message": {
                            "message": "Execution {{ execution.id }} fail"
                          },
                          "extra": {
                            "Namespace": "{{ flow.namespace }}",
                            "Flow ID": "{{ flow.id }}",
                            "Execution ID": "{{ execution.id }}",
                            "Link": "http://localhost:8080/ui/executions/{{flow.namespace}}/{{flow.id}}/{{execution.id}}"
                          }
                      }"""
        ),
    }
)
public class SentryAlert extends Task implements RunnableTask<VoidOutput> {
    public static final String SENTRY_VERSION = "7";
    public static final String SENTRY_CLIENT = "java";
    public static final String SENTRY_DSN_REGEXP = "^(https?://[a-f0-9]+@o[0-9]+\\.ingest\\.sentry\\.io/[0-9]+)$";

    private static final String DEFAULT_PAYLOAD = """
        {
          "timestamp": "{{ execution.startDate }}",
          "platform": "java",
          "level": "error",
          "message": {
            "message": "Execution {{ execution.id }} fail"
          },
          "extra": {
            "Namespace": "{{ flow.namespace }}",
            "Flow ID": "{{ flow.id }}",
            "Execution ID": "{{ execution.id }}"
          }
        }""";

    @Schema(
        title = "Sentry DSN"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String dsn;

    @Schema(
        title = "Sentry event payload"
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String dsn = runContext.render(this.dsn);

        String url = dsn;
        if (dsn.matches(SENTRY_DSN_REGEXP)) {
            String protocol = dsn.split("://")[0];
            String publicKey = dsn.split("@")[0].replace(protocol + "://", "");
            String host = dsn.split("@")[1].split("/")[0];
            String projectId = dsn.split("@")[1].split("/")[1];
            /*
            To make passing the correct API endpoint URL easier, 
            users only need to provide the Sentry DSN, and we parse the required attributes for the URL
            using the format https://{HOST}/api/{PROJECT_ID}/store/?sentry_version=7&sentry_client=java&sentry_key={PUBLIC_KEY}
            */
            url = "%s://%s/api/%s/store/?sentry_version=%s&sentry_client=%s&sentry_key=%s" 
                .formatted(protocol, host, projectId, SENTRY_VERSION, SENTRY_CLIENT, publicKey);
        }

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = this.payload != null ? runContext.render(this.payload) : runContext.render(DEFAULT_PAYLOAD.strip());

            runContext.logger().debug("Sent the following Sentry event: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload));
        }

        return null;
    }
}
