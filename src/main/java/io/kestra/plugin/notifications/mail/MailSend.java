package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
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
import jakarta.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an automated email from a workflow"
)
@Plugin(
    examples = {
        @Example(
            title = "Send an email on a failed flow execution",
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
                  - id: send_email
                    type: io.kestra.plugin.notifications.mail.MailSend
                    from: hello@kestra.io
                    to: hello@kestra.io
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    host: mail.privateemail.com
                    port: 465 # or 587
                    subject: "Kestra workflow failed for the flow {{flow.id}} in the namespace {{flow.namespace}}"
                    htmlTextContent: "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                """
        )
    }
)
public class MailSend extends Task implements RunnableTask<VoidOutput> {
    /* Server info */
    @Schema(
        title = "The email server host"
    )
    protected Property<String> host;

    @Schema(
        title = "The email server port"
    )
    private Property<Integer> port;

    @Schema(
        title = "The email server username"
    )
    protected Property<String> username;

    @Schema(
        title = "The email server password"
    )
    protected Property<String> password;

    @Schema(
        title = "The optional transport strategy",
        description = "Will default to SMTPS if left empty"
    )
    @Builder.Default
    private final Property<TransportStrategy> transportStrategy = Property.of(TransportStrategy.SMTPS);

    @Schema(
        title = "Integer value in milliseconds. Default is 10000 milliseconds, i.e. 10 seconds",
        description = "It controls the maximum timeout value when sending emails"
    )
    @Builder.Default
    private final Property<Integer> sessionTimeout = Property.of(10000);

    /* Mail info */
    @Schema(
        title = "The address of the sender of this email"
    )
    protected Property<String> from;

    @Schema(
        title = "Email address(es) of the recipient(s). Use semicolon as delimiter to provide several email addresses",
        description = "Note that each email address must be compliant with the RFC2822 format"
    )
    protected Property<String> to;

    @Schema(
        title = "One or more 'Cc' (carbon copy) optional recipient email address. Use semicolon as delimiter to provide several addresses",
        description = "Note that each email address must be compliant with the RFC2822 format"
    )
    protected Property<String> cc;

    @Schema(
        title = "The optional subject of this email"
    )
    protected Property<String> subject;

    @Schema(
        title = "The optional email message body in HTML text",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    protected Property<String> htmlTextContent;

    @Schema(
        title = "The optional email message body in plain text",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    protected Property<String> plainTextContent;

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

        final String htmlContent = runContext.render(runContext.render(this.htmlTextContent).as(String.class).orElse(null));
        final String textContent = runContext.render(this.plainTextContent).as(String.class).isEmpty() ?
            "Please view this email in a modern email client" :
            runContext.render(runContext.render(this.plainTextContent).as(String.class).get());

        // Building email to send
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
            .to(runContext.render(to).as(String.class).orElseThrow())
            .from(runContext.render(from).as(String.class).orElseThrow())
            .withSubject(runContext.render(subject).as(String.class).orElse(null))
            .withHTMLText(htmlContent)
            .withPlainText(textContent)
            .withReturnReceiptTo();

        if (this.attachments != null) {
            builder.withAttachments(this.attachmentResources(this.attachments, runContext));
        }

        if (this.embeddedImages != null) {
            builder.withEmbeddedImages(this.attachmentResources(this.embeddedImages, runContext));
        }

        runContext.render(cc).as(String.class).ifPresent(builder::cc);

        Email email = builder.buildEmail();

        // Building mailer to send email
        Mailer mailer = MailerBuilder
            .withSMTPServer(
                runContext.render(this.host).as(String.class).orElse(null),
                runContext.render(this.port).as(Integer.class).orElse(null),
                runContext.render(this.username).as(String.class).orElse(null),
                runContext.render(this.password).as(String.class).orElse(null)
            )
            .withTransportStrategy(runContext.render(transportStrategy).as(TransportStrategy.class).orElse(TransportStrategy.SMTPS))
            .withSessionTimeout(runContext.render(sessionTimeout).as(Integer.class).orElse(10000))
            // .withDebugLogging(true)
            .buildMailer();

        mailer.sendMail(email);

        return null;
    }

    private List<AttachmentResource> attachmentResources(List<Attachment> list, RunContext runContext) throws Exception {
        return list
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.storage()
                    .getFile(URI.create(runContext.render(attachment.getUri()).as(String.class).orElseThrow()));

                return new AttachmentResource(
                    runContext.render(attachment.getName()).as(String.class).orElseThrow(),
                    new ByteArrayDataSource(inputStream, runContext.render(attachment.getContentType()).as(String.class).orElseThrow())
                );
            }))
            .collect(Collectors.toList());
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Attachment {
        @Schema(
            title = "An attachment URI from Kestra internal storage"
        )
        @NotNull
        private Property<String> uri;

        @Schema(
            title = "The name of the attachment (eg. 'filename.txt')"
        )
        @NotNull
        private Property<String> name;

        @Schema(
            title = "One or more 'Cc' (carbon copy) optional recipient email address(es). Use semicolon as a delimiter to provide several addresses",
            description = "Note that each email address must be compliant with the RFC2822 format"
        )
        @NotNull
        @Builder.Default
        private Property<String> contentType = Property.of("application/octet-stream");
    }
}
