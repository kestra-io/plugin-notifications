package io.kestra.plugin.notifications.sendgrid;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;

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
                namespace: company.team

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
    private Property<List<String>> cc;

    @Schema(
        title = "The optional subject of this email"
    )
    private Property<String> subject;

    @Schema(
        title = "The optional email message body in HTML",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    protected Property<String> htmlContent;

    @Schema(
        title = "The optional email message body in plain text",
        description = "Both text and HTML can be provided, which will be offered to the email client as alternative content" +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely"
    )
    protected Property<String> textContent;

    @Schema(
        title = "Adds an attachment to the email message",
        description = "The attachment will be shown in the email client as separate files available for download, or displayed " +
            "inline if the client supports it (for example, most browsers display PDF's in a popup window)"
    )
    private List<Attachment> attachments;

    @Schema(
        title = "Adds image data to this email that can be referred to from the email HTML body",
        description = "The provided images are assumed to be of MIME type png, jpg or whatever the email client supports as valid image that can be embedded in HTML content"
    )
    private List<Attachment> embeddedImages;

    @Override
    public SendGridMailSend.Output run(RunContext runContext) throws Exception {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Logger logger = runContext.logger();

        logger.debug("Sending an email to {}", runContext.render(to));

        Mail mail = new Mail();

        Email fromEmail = new Email(runContext.render(this.from));
        mail.setFrom(fromEmail);

        Personalization personalization = new Personalization();

        runContext.render(this.to).stream().map(Email::new).forEach(personalization::addTo);

        personalization.setSubject(runContext.render(this.subject).as(String.class).orElse(null));

        if (this.textContent != null) {
            var renderedText = runContext.render(this.textContent).as(String.class);
            final String textContent = renderedText.isEmpty() ? "Please view this email in a modern email client" : renderedText.get();
            Content plainTextContent = new Content(ContentType.TEXT_PLAIN.getMimeType(), textContent);
            mail.addContent(plainTextContent);
        }

        if (runContext.render(this.htmlContent).as(String.class).isPresent()) {
            Content htmlContent = new Content(ContentType.TEXT_HTML.getMimeType(), runContext.render(this.htmlContent).as(String.class).get());
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

        final List<String> renderedCcList = runContext.render(this.cc).asList(String.class);
        if (!renderedCcList.isEmpty()) {
            renderedCcList.stream().map(Email::new).forEach(personalization::addCc);
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

        if (statusCode/100 != 2) {
          throw new RuntimeException("SendGrid API failed with status code: " + statusCode + " and body: " + body);
        }

        return Output.builder().body(body).headers(headers).statusCode(statusCode).build();
    }

    private List<Attachments> attachmentResources(List<Attachment> list, RunContext runContext) throws Exception {
        return list
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.storage()
                    .getFile(URI.create(runContext.render(attachment.getUri()).as(String.class).get()));

                return new Attachments.Builder(runContext.render(attachment.getName()).as(String.class).get(), inputStream)
                    .withType(runContext.render(attachment.getContentType()).as(String.class).get()).build();
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

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String body;
        private Map<String, String> headers;
        private int statusCode;
    }
}
