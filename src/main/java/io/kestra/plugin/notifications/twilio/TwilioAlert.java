package io.kestra.plugin.notifications.twilio;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
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

import jakarta.validation.constraints.NotBlank;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Twilio message using an notification API",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://www.twilio.com/docs/notify/send-notifications#sending-notifications\">Twilio documentation</a> for more details.."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Twilio notification on a failed flow execution",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.notifications.twilio.TwilioAlert
                    url: "{{ secret('TWILIO_NOTIFICATION_URL') }}" # https://notify.twilio.com/v1/Services/ISXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Notifications
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    payload: |
                      {
                        "identity": "0000001"
                      }
                """
        ),
        @Example(
            title = "Send a Twilio message via incoming notification API",
            full = true,
            code = """
                id: twilio_alert
                namespace: company.team

                tasks:
                  - id: send_twilio_message
                    type: io.kestra.plugin.notifications.twilio.TwilioAlert
                    url: "{{ secret('TWILIO_NOTIFICATION_URL') }}"
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    payload: |
                      {
                        "identity": "0000001"
                      }
                """
        ),
    }
)
public class TwilioAlert extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Twilio notification URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Twilio message payload"
    )
    protected Property<String> payload;

    @Schema(
        title = "Twilio account SID"
    )
    @NotBlank
    @PluginProperty(dynamic = true)
    protected String accountSID;

    @Schema(
        title = "Twilio authentication token"
    )
    @NotBlank
    @PluginProperty(dynamic = true)
    protected String authToken;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(url))) {
            String payload = runContext.render(this.payload).as(String.class).orElse(null);

            runContext.logger().debug("Send Twilio notification: {}", payload);

            client.toBlocking().retrieve(HttpRequest.POST(url, payload).header(runContext.render(accountSID), runContext.render(authToken)));
        }

        return null;
    }
}
