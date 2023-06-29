package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.simplejavamail.api.email.AttachmentResource;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task to send an email"
)
public class MailSend extends Task implements RunnableTask<VoidOutput> {
    /* Server info */
    @Schema(
        title = "The email server host"
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "The email server port"
    )
    @PluginProperty(dynamic = true)
    private Integer port;

    @Schema(
        title = "The email server username"
    )
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(
        title = "The email server password"
    )
    @PluginProperty(dynamic = true)
    private String password;

    @Builder.Default
    @Schema(
        title = "The optional transport strategy",
        description = "Will default to SMTPS if left empty"
    )
    private final TransportStrategy transportStrategy = TransportStrategy.SMTPS;

    @Builder.Default
    @Schema(
        title = "Integer value in milliseconds. Default is 1000 milliseconds, i.e. 1 second",
        description = "It controls the maximum timeout value when sending emails"
    )
    private final Integer sessionTimeout = 1000;

    /* Mail info */
    @Schema(
        title = "The address of the sender of this email"
    )
    @PluginProperty(dynamic = true)
    private String from;

    @Schema(
        title = "Email address(es) of the recipient(s). Use semicolon as delimiter to provide several email addresses",
        description = "Note that each email address must be compliant with the RFC2822 format"
    )
    @PluginProperty(dynamic = true)
    private String to;

    @Schema(
        title = "One or more 'Cc' (carbon copy) optional recipient email address. Use semicolon as delimiter to provide several addresses",
        description = "Note that each email address must be compliant with the RFC2822 format"
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
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    @PluginProperty(dynamic = true)
    protected String htmlTextContent;

    @Schema(
        title = "Adds an attachment to the email message",
        description = "The attachment will be shown in the email client as separate files available for download, or displayed " +
            "inline if the client supports it (for example, most browsers display PDF's in a popup window)"
    )
    @PluginProperty(dynamic = true)
    private List<Attachment> attachments;

    @Schema(
        title = "Adds image data to this email that can be referred to from the email HTML body",
        description = "The provided images are assumed to be of MIME type png, jpg or whatever the email client supports as valid image that can be embedded in HTML content"
    )
    @PluginProperty(dynamic = true)
    private List<Attachment> embeddedImages;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Logger logger = runContext.logger();

        logger.debug("Sending an email to {}", to);

        final String htmlContent = runContext.render(this.htmlTextContent);

        // Building email to send
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
            .to(runContext.render(to))
            .from(runContext.render(from))
            .withSubject(runContext.render(subject))
            .withHTMLText(htmlContent)
            .withPlainText("Please view this email in a modern email client")
            .withReturnReceiptTo();

        if (this.attachments != null) {
            builder.withAttachments(this.attachmentResources(this.attachments, runContext));
        }

        if (this.embeddedImages != null) {
            builder.withEmbeddedImages(this.attachmentResources(this.embeddedImages, runContext));
        }

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

    private List<AttachmentResource> attachmentResources(List<Attachment> list, RunContext runContext) throws Exception {
        return list
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.uriToInputStream(URI.create(runContext.render(attachment.getUri())));

                return new AttachmentResource(
                    runContext.render(attachment.getName()),
                    new ByteArrayDataSource(inputStream, runContext.render(attachment.getContentType()))
                );
            }))
            .collect(Collectors.toList());
    }

    @Getter
    @Builder
    @Jacksonized
    @Introspected
    public static class Attachment {
        @Schema(
            title = "An attachment URI from Kestra internal storage"
        )
        @PluginProperty(dynamic = true)
        @NotNull
        private String uri;

        @Schema(
            title = "The name of the attachment (eg. 'filename.txt')"
        )
        @PluginProperty(dynamic = true)
        @NotNull
        private String name;

        @Schema(
            title = "One or more 'Cc' (carbon copy) optional recipient email address(es). Use semicolon as a delimiter to provide several addresses",
            description = "Note that each email address must be compliant with the RFC2822 format"
        )
        @PluginProperty(dynamic = true)
        @NotNull
        @Builder.Default
        private String contentType = "application/octet-stream";
    }
}
