package org.kestra.task.notifications.mail;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;
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
@Documentation(
    description = "Generic task to send a mail."
)
public class MailSend extends Task implements RunnableTask<VoidOutput> {
    /* Server info */
    @InputProperty(
        description = "The mail server host"
    )
    private String host;

    @InputProperty(
        description = "The mail server port"
    )
    private Integer port;

    @InputProperty(
        description = "The mail server username"
    )
    private String username;

    @InputProperty(
        description = "The mail server password"
    )
    private String password;

    @Builder.Default
    @InputProperty(
        description = "The optional transport strategy",
        body = "Will default to SMTPS if left empty."
    )
    private TransportStrategy transportStrategy = TransportStrategy.SMTPS;

    @Builder.Default
    @InputProperty(
        description = "Controls the timeout to use when sending emails",
        body = "It affects socket connect-, read- and write timeouts"
    )
    private Integer sessionTimeout = 1000;

    /* Mail info */
    @InputProperty(
        description = "The address of the sender of this email"
    )
    private String from;

    @InputProperty(
        description = "The recipient email address",
        body = "Note that the email address must be an RFC2822 format compliant address."
    )
    private String to;

    @InputProperty(
        description = "The optional subject of this email"
    )
    private String subject;

    @InputProperty(
        description = "The optional email message body in HTML text",
        body = "Both text and HTML can be provided, which will be offered to the email client as alternative content." +
            " Email clients that support it, will favor HTML over plain text and ignore the text body completely",
        dynamic = true
    )
    protected String htmlTextContent;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

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
