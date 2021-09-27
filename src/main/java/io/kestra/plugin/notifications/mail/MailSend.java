package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.slf4j.Logger;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Generic task to send a mail."
)
public class MailSend extends Task implements RunnableTask<VoidOutput> {
    /* Server info */
    @Schema(
        title = "The mail server host"
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "The mail server port"
    )
    @PluginProperty(dynamic = true)
    private Integer port;

    @Schema(
        title = "The mail server username"
    )
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(
        title = "The mail server password"
    )
    @PluginProperty(dynamic = true)
    private String password;

    @Builder.Default
    @Schema(
        title = "The optional transport strategy",
        description = "Will default to SMTPS if left empty."
    )
    private final TransportStrategy transportStrategy = TransportStrategy.SMTPS;

    @Builder.Default
    @Schema(
        title = "Controls the timeout to use when sending emails",
        description = "It affects socket connect-, read- and write timeouts"
    )
    private final Integer sessionTimeout = 1000;

    /* Mail info */
    @Schema(
        title = "The address of the sender of this email"
    )
    @PluginProperty(dynamic = true)
    private String from;

    @Schema(
        title = "One or more recipient email address. Use semicolon as delimiter to provide several addresses",
        description = "Note that each email address must be an RFC2822 format compliant address"
    )
    @PluginProperty(dynamic = true)
    private String to;

    @Schema(
        title = "One or more 'Cc' (carbon copy) optional recipient email address. Use semicolon as delimiter to provide several addresses",
        description = "Note that each email address must be an RFC2822 format compliant address."
    )
    @PluginProperty(dynamic = true)
    private String cc;

    @Schema(
        title = "The optional subject of this email"
    )
    @PluginProperty(dynamic = true)
    private String subject;

    @Schema(
        title = "The optional email message body in HTML text",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content." +
            " Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    @PluginProperty(dynamic = true)
    protected String htmlTextContent;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        logger.debug("Sending email to {} ...", to);

        final String htmlContent = runContext.render(this.htmlTextContent);

        // Building email to send
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
            .to(runContext.render(to))
            .from(runContext.render(from))
            .withSubject(runContext.render(subject))
            .withHTMLText(htmlContent)
            .withPlainText("Please view this email in a modern email client!")
            .withReturnReceiptTo();

        if (this.cc != null) {
            builder.cc(runContext.render(cc));
        }

        Email email = builder.buildEmail();

        // Building mailer to send email
        Mailer mailer = MailerBuilder
            .withSMTPServer(
                runContext.render(this.host),
                this.port,
                runContext.render(this.username),
                runContext.render(this.password)
            )
            .withTransportStrategy(transportStrategy)
            .withSessionTimeout(sessionTimeout)
            // .withDebugLogging(true)
            .buildMailer();

        mailer.sendMail(email);

        return null;
    }
}
