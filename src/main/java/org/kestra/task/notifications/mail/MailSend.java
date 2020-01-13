package org.kestra.task.notifications.mail;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.config.TransportStrategy;
import org.slf4j.Logger;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class MailSend extends Task implements RunnableTask {
    /* Server info */
    private String host;
    private Integer port;
    private String username, password;

    @Builder.Default
    private TransportStrategy transportStrategy = TransportStrategy.SMTPS;

    @Builder.Default
    private Integer sessionTimeout = 1000;

    /* Mail info */
    private String from, to, subject;
    protected String htmlTextContent;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        logger.debug("Sending email to {} ...", to);

        final String htmlContent = runContext.render(this.htmlTextContent);

        // Building email to send
        Email email = EmailBuilder.startingBlank()
            .to(to)
            .from(from)
            .withSubject(subject)
            .withHTMLText(htmlContent)
            .withPlainText("Please view this email in a modern email client!")
            .withReturnReceiptTo()
            .buildEmail();

        // Building mailer to send email
        Mailer mailer = MailerBuilder
            .withSMTPServer(this.host, this.port, this.username, this.password)
            .withTransportStrategy(transportStrategy)
            .withSessionTimeout(sessionTimeout)
            // .withDebugLogging(true)
            .buildMailer();

        mailer.sendMail(email);

        return null;
    }
}