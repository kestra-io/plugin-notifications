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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class MailIncomingWebhook extends Task implements RunnableTask {

    /* Server info */
    private String host;
    private Integer port;
    private String username, password;
    private String transportStrategy;

    /* Used to output mail debug */
    private boolean debug;
    /* Timeout in ms */
    private Integer timeout;

    /* Mail info */
    private String from, to, subject;

    private static final String DEFAULT_PLAIN_TEXT_CONTENT = "Please view this email in a modern email client!";
    private static final int DEFAULT_TIMEOUT = 10 * 1000;

    protected String htmlTextContent;

    @Builder.Default
    transient private TransportStrategy ts = TransportStrategy.SMTPS;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        runContext.logger(this.getClass()).debug("Sending email to {} ...", to);

        final String htmlContent = runContext.render(this.htmlTextContent);

        // Building email to send
        Email email = EmailBuilder.startingBlank()
            .to(to)
            .from(from)
            .withSubject(subject)
            .withHTMLText(htmlContent)
            .withPlainText(DEFAULT_PLAIN_TEXT_CONTENT)
            .withReturnReceiptTo()
            .buildEmail();

        // Building mailer to send email
        int timeout = (getTimeout() == null ? DEFAULT_TIMEOUT : getTimeout());
        try {
            transportStrategy = (transportStrategy == null ? "" : transportStrategy.toUpperCase());
            ts = TransportStrategy.valueOf(transportStrategy);
        } catch (Exception e) {
            runContext.logger(
                this.getClass()).warn("Invalid value [{}] provided for transport strategy. Switched to default value {}",
                transportStrategy, ts.name());
            runContext.logger(this.getClass()).warn("Supported values are {}", (Object) TransportStrategy.values());
        }

        Mailer mailer = MailerBuilder
            .withSMTPServer(this.host, this.port, this.username, this.password)
            .withTransportStrategy(ts)
            .withSessionTimeout(timeout)
            .withDebugLogging(debug)
            .buildMailer();

        mailer.sendMail(email);

        return null;
    }
}