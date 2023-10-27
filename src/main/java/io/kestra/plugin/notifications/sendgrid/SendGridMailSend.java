package io.kestra.plugin.notifications.sendgrid;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an automated SendGrid email from a workflow"
)
@Plugin(
    examples = {
        @Example(
            title = "Send an email on a failed flow execution",
            full = true,
            code = """
                id: unreliable_flow
                namespace: prod

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: send_email
                    type: io.kestra.plugin.notifications.sendgrid.SendGridMailSend
                    from: hello@kestra.io
                    to:
                      - hello@kestra.io
                    sendgridApiKey: "{{ secret('SENDGRID_API_KEY') }}"
                    subject: "Kestra workflow failed for the flow {{flow.id}} in the namespace {{flow.namespace}}"
                    htmlTextContent: "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                """
        )
    }
)
public class SendGridMailSend extends Task implements RunnableTask<SendGridMailSend.Output> {
    /* Server info */

    @Schema(
        title = "The SendGrid API KEY"
    )
    @NotBlank
    @PluginProperty(dynamic = true)
    private String sendgridApiKey;

    /* Mail info */
    @Schema(
        title = "The address of the sender of this email"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    private String from;

    @Schema(
        title = "Email address(es) of the recipient(s)",
        description = "Note that each email address must be compliant with the RFC2822 format"
    )
    @NotEmpty
    @PluginProperty(dynamic = true)
    private List<String> to;

    @Schema(
        title = "One or more 'Cc' (carbon copy) optional recipient(s) email address(es)",
        description = "Note that each email address must be compliant with the RFC2822 format"
    )
    @PluginProperty(dynamic = true)
    private List<String> cc;

    @Schema(
        title = "The optional subject of this email"
    )
    @PluginProperty(dynamic = true)
    private String subject;

    @Schema(
        title = "The optional email message body in HTML",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    @PluginProperty(dynamic = true)
    protected String htmlContent;

    @Schema(
        title = "The optional email message body in text",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    @PluginProperty(dynamic = true)
    protected String textContent;

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
    public SendGridMailSend.Output run(RunContext runContext) throws Exception {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Logger logger = runContext.logger();

        logger.debug("Sending an email to {}", to);

        Mail mail = new Mail();

        Personalization personalization = new Personalization();

        Email fromEmail = new Email(runContext.render(this.from));
        personalization.setFrom(fromEmail);

        runContext.render(this.to).stream().map(Email::new).forEach(personalization::addTo);

        personalization.setSubject(runContext.render(this.subject));

        if (this.textContent != null) {
            Content plainTextContent = new Content(ContentType.TEXT_PLAIN.getMimeType(), runContext.render(this.htmlContent));
            mail.addContent(plainTextContent);
        }

        if (this.htmlContent != null) {
            Content htmlContent = new Content(ContentType.TEXT_HTML.getMimeType(), runContext.render(this.htmlContent));
            mail.addContent(htmlContent);
        }

        if (this.attachments != null) {
            this.attachmentResources(this.attachments, runContext).stream()
                .peek(attachment -> attachment.setDisposition("attachment"))
                .forEach(mail::addAttachments);
        }

        if (this.embeddedImages != null) {
            this.attachmentResources(this.embeddedImages, runContext).stream()
                .peek(attachment -> attachment.setDisposition("inline"))
                .forEach(mail::addAttachments);
        }

        if (this.cc != null) {
            runContext.render(this.cc).stream().map(Email::new).forEach(personalization::addCc);
        }
        mail.addPersonalization(personalization);

        SendGrid sendGrid = new SendGrid(runContext.render(this.sendgridApiKey));

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response api = sendGrid.api(request);
        String body = api.getBody();
        Map<String, String> headers = api.getHeaders();
        int statusCode = api.getStatusCode();

        return Output.builder().body(body).headers(headers).statusCode(statusCode).build();
    }

    private List<Attachments> attachmentResources(List<Attachment> list, RunContext runContext) throws Exception {
        return list
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.uriToInputStream(URI.create(runContext.render(attachment.getUri())));

                return new Attachments.Builder(runContext.render(attachment.getName()), inputStream)
                    .withType(runContext.render(attachment.getContentType())).build();
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

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String body;
        private Map<String, String> headers;
        private int statusCode;
    }
}
