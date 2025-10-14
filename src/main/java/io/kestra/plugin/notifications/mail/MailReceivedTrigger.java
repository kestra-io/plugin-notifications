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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on new email messages.",
    description = "Monitor a mailbox for new emails via IMAP or POP3 protocols."
)
@Plugin(
    examples = {
        @Example(
            title = "Monitor Gmail inbox for new emails",
            full = true,
            code = """
                id: email_monitor
                namespace: company.team

                tasks:
                  - id: process_email
                    type: io.kestra.core.tasks.log.Log
                    message: |
                      New email received:
                      Subject: {{ trigger.latestEmail.subject }}
                      From: {{ trigger.latestEmail.from }}
                      Date: {{ trigger.latestEmail.date }}

                triggers:
                  - id: gmail_inbox_trigger
                    type: io.kestra.plugin.notifications.mail.MailReceivedTrigger
                    protocol: IMAP
                    host: imap.gmail.com
                    port: 993
                    username: "{{ secret('GMAIL_USERNAME') }}"
                    password: "{{ secret('GMAIL_PASSWORD') }}"
                    folder: INBOX
                    interval: PT30S
                    ssl: true
                """
        )
    }
)
public class MailReceivedTrigger extends AbstractMailTrigger
        implements PollingTriggerInterface, TriggerOutput<MailService.Output> {

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        MailService.MailConfiguration  mailConfig = renderMailConfiguration(runContext);

        try {
            ZonedDateTime lastCheckTime = context.getNextExecutionDate() != null
                    ? context.getNextExecutionDate().minus(mailConfig.interval)
                    : ZonedDateTime.now().minus(Duration.ofHours(1));

            List<MailService.EmailData> newEmails = MailService.fetchNewEmails(runContext, mailConfig.protocol, mailConfig.host, mailConfig.port,
                    mailConfig.username, mailConfig.password, mailConfig.folder, mailConfig.ssl, mailConfig.trustAllCertificates, lastCheckTime);

            if (newEmails.isEmpty()) {
                return Optional.empty();
            }

            MailService.EmailData latest = newEmails.stream()
                    .max(Comparator.comparing(MailService.EmailData::getDate))
                    .orElse(newEmails.getFirst());

            MailService.Output output = MailService.Output.builder()
                    .latestEmail(latest)
                    .total(newEmails.size())
                    .emails(newEmails)
                    .build();

            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);
            return Optional.of(execution);

        } catch (Exception e) {
            runContext.logger().error("Error checking for new emails", e);
            throw new RuntimeException("Failed to fetch emails: " + e.getMessage(), e);
        }
    }
}