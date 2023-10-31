package io.kestra.plugin.notifications.sentry;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.ExecutionInterface;
import io.kestra.plugin.notifications.services.ExecutionService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Sentry alert with the execution information",
    description = """
    The alert message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration and the final status of the execution, and (if failed) the task that led to a failure.\n\n Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [SentryAlert](https://kestra.io/plugins/plugin-notifications/tasks/sentry/io.kestra.plugin.notifications.sentry.sentryalert) task. \n\n The only required input is a DSN string value, which you can find when you go to your Sentry project settings and go to the section `Client Keys (DSN)`. For more detailed description of how to find your DSN, visit the [following Sentry documentation](https://docs.sentry.io/product/sentry-basics/concepts/dsn-explainer/#where-to-find-your-dsn).\n\n You can customize the alert `payload`, which is a JSON object. For more information about the payload, check the [Sentry Event Payloads documentation](https://develop.sentry.dev/sdk/event-payloads/). \n\n The `level` parameter is the severity of the issue. The task documentation lists all available options including `DEBUG`, `INFO`, `WARNING`, `ERROR`, `FATAL`. The default value is `ERROR`."""
)
@Plugin(
    examples = {
        @Example(
            title = "This monitoring flow is triggered anytime a flow fails in the `prod` namespace. It then sends a Sentry alert with the execution information. You can fully customize the [trigger conditions](https://kestra.io/plugins/core#conditions).",
            full = true,
            code = """
                id: failure_alert
                namespace: prod.monitoring

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.sentry.SentryExecution
                    executionId: "{{ trigger.executionId} }"
                    transaction: "/execution/id/{{ trigger.executionId} }"
                    dsn: "{{ secret('SENTRY_DSN') }}" # format: https://xxx@xxx.ingest.sentry.io/xxx
                    level: ERROR
                    executionId: "{{ trigger.executionId }}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.core.models.triggers.types.Flow
                    conditions:
                      - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.core.models.conditions.types.ExecutionNamespaceCondition
                        namespace: prod
                        prefix: true"""
        )
    }
)
public class SentryExecution extends SentryTemplate implements ExecutionInterface {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "sentry-template.peb";
        this.templateRenderMap = ExecutionService.executionMap(runContext, this);

        return super.run(runContext);
    }
}
