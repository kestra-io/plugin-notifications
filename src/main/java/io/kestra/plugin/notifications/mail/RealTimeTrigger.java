package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when an email is received in real-time.",
    description = "Monitor a mailbox for new emails via IMAP or POP3 protocols and create one execution per email received. " +
                 "If you would like to process multiple emails in batch, use the MailReceivedTrigger instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Monitor Gmail inbox for new emails in real-time",
            full = true,
            code = """
                id: realtime_email_monitor
                namespace: company.team

                tasks:
                  - id: process_email
                    type: io.kestra.core.tasks.log.Log
                    message: |
                      Real-time email received:
                      Subject: {{ trigger.subject }}
                      From: {{ trigger.latest.from }}
                      Date: {{ trigger.latest.date }}
                      Body: {{ trigger.latest.body }}

                triggers:
                  - id: realtime_gmail_trigger
                    type: io.kestra.plugin.notifications.mail.RealTimeTrigger
                    protocol: IMAP
                    host: imap.gmail.com
                    port: 993
                    username: "{{ secret('GMAIL_USERNAME') }}"
                    password: "{{ secret('GMAIL_PASSWORD') }}"
                    folder: INBOX
                    ssl: true
                    interval: PT10S
                """
        ),
        @Example(
            title = "Monitor POP3 mailbox in real-time",
            code = """
                triggers:
                  - id: realtime_pop3_trigger
                    type: io.kestra.plugin.notifications.mail.RealTimeTrigger
                    protocol: POP3
                    host: pop.example.com
                    port: 995
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    ssl: true
                    interval: PT5S
                """
        )
    }
)
public class RealTimeTrigger extends AbstractMailTrigger
        implements RealtimeTriggerInterface, TriggerOutput<MailService.Output> {

    @Override
    public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        MailService.MailConfiguration mailConfig=renderMailConfiguration(runContext);

        runContext.logger().info("Starting real-time email monitoring on {}:{} with interval {}",
                mailConfig.host, mailConfig.port, mailConfig.interval);

        return Flux.interval(mailConfig.interval)
                .doOnNext(tick -> runContext.logger().debug("Checking for new emails (tick: {})", tick))
                .flatMap(tick -> {
                    try {
                        ZonedDateTime lastCheckTime = ZonedDateTime.now().minus(mailConfig.interval.multipliedBy(2));

                        List<MailService.EmailData> newEmails = MailService.fetchNewEmails(runContext, mailConfig.protocol, mailConfig.host, mailConfig.port,
                            mailConfig.username, mailConfig.password, mailConfig.folder, mailConfig.ssl, mailConfig.trustAllCertificates, lastCheckTime);

                        return Flux.fromIterable(newEmails)
                                .map(emailData -> {
                                    runContext.logger().info("Real-time trigger: New email from '{}' with subject '{}'",
                                            emailData.getFrom(), emailData.getSubject());
                                    return TriggerService.generateRealtimeExecution(this, conditionContext, context, emailData);
                                });
                    } catch (Exception e) {
                        runContext.logger().error("Error in real-time email monitoring", e);
                        return Flux.empty();
                    }
                })
                .onErrorContinue((throwable, o) -> runContext.logger().error("Error in real-time email stream", throwable));
    }
}