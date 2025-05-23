package io.kestra.plugin.notifications.opsgenie;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;

import io.micronaut.http.HttpHeaders;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an alert to Opsgenie.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications. Check the <a href=\"https://docs.opsgenie.com/docs/alert-api\">Opsgenie documentation</a> for more details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a failed flow alert to Opsgenie.",
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
                    type: io.kestra.plugin.notifications.opsgenie.OpsgenieAlert
                    url: "{{ secret('OPSGENIE_REQUEST') }}" # https://api.opsgenie.com/v2/alerts/requests/xxx000xxxxx
                    payload: |
                      {
                        "message":"Kestra Opsgenie alert",
                        "alias":"ExecutionError",
                        "responders":[
                            {"id":"4513b7ea-3b91-438f-b7e4-e3e54af9147c","type":"team"},
                            {"id":"bb4d9938-c3c2-455d-aaab-727aa701c0d8","type":"user"},
                            {"id":"aee8a0de-c80f-4515-a232-501c0bc9d715","type":"escalation"},
                            {"id":"80564037-1984-4f38-b98e-8a1f662df552","type":"schedule"}
                         ],
                        "visibleTo":[
                            {"id":"4513b7ea-3b91-438f-b7e4-e3e54af9147c","type":"team"},
                            {"id":"bb4d9938-c3c2-455d-aaab-727aa701c0d8","type":"user"}
                         ],
                        "tags":["ExecutionFail","Error","Execution"],
                        "priority":"P1"
                      }
                    authorizationToken: sampleAuthorizationToken
                """
        ),
        @Example(
            title = "Send a Opsgenie alert.",
            full = true,
            code = """
                id: opsgenie_incoming_webhook
                namespace: company.team

                tasks:
                  - id: send_opsgenie_message
                    type: io.kestra.plugin.notifications.opsgenie.OpsgenieAlert
                    url: "{{ secret('OPSGENIE_REQUEST') }}"
                    payload: |
                      {
                        "message":"Kestra Opsgenie alert",
                        "alias":"Some Execution",
                        "responders":[
                            {"id":"4513b7ea-3b91-438f-b7e4-e3e54af9147c","type":"team"},
                            {"id":"bb4d9938-c3c2-455d-aaab-727aa701c0d8","type":"user"}
                         ],
                        "visibleTo":[
                            {"id":"4513b7ea-3b91-438f-b7e4-e3e54af9147c","type":"team"},
                            {"id":"bb4d9938-c3c2-455d-aaab-727aa701c0d8","type":"user"}
                         ],
                        "tags":["Execution"],
                        "priority":"P2"
                      }
                    authorizationToken: sampleAuthorizationToken
                """
        ),
    }
)
public class OpsgenieAlert extends AbstractHttpOptionsTask {

    @Schema(
        title = "Alert creation URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String url;

    @Schema(
        title = "Opsgenie alert payload"
    )
    protected Property<String> payload;

    @Schema(
        title = "GenieKey. Authorization token from Opsgenie"
    )
    protected Property<String> authorizationToken;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(this.payload).as(String.class).orElse(null);

            runContext.logger().debug("Send Opsgenie alert: {}", payload);

            HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext)
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .addHeader(HttpHeaders.AUTHORIZATION, runContext.render(authorizationToken).as(String.class).orElse(null))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
                    .content(payload)
                    .build());

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() == 200) {
                runContext.logger().info("Request succeeded");
            }
        }

        return null;
    }
}
