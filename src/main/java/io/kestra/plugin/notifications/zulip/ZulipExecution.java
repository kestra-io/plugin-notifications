package io.kestra.plugin.notifications.zulip;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
@Deprecated
@Schema(
    title = "Send a Zulip message with the execution information.",
    description = """
        The message will include a link to the execution page in the UI along with the execution ID, namespace, flow name, the start date, duration, and the final status of the execution. If failed, then the task that led to the failure is specified.

        Use this notification task only in a flow that has a [Flow trigger](https://kestra.io/docs/administrator-guide/monitoring#alerting). Don't use this notification task in `errors` tasks. Instead, for `errors` tasks, use the [ZulipIncomingWebhook](https://kestra.io/plugins/plugin-notifications/zulip/io.kestra.plugin.notifications.zulip.zulipincomingwebhook) task.

        This task is deprecated since Kestra v1.1.11 and has been replaced by `plugin-zulip (io.kestra.plugin.zulip)`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Zulip notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.zulip.ZulipExecution
                    url: "{{ secret('ZULIP_WEBHOOK') }}" # format: https://yourZulipDomain.zulipchat.com/api/v1/external/INTEGRATION_NAME?api_key=API_KEY
                    channel: "#general"
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: prod
                        prefix: true
                """
        )
    }
)
public class ZulipExecution extends ZulipTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("zulip-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
