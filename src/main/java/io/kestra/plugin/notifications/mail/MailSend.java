package io.kestra.plugin.notifications.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
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
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an automated email from a workflow."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an email on a failed flow execution.",
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
        ),
        @Example(
            title = "Send an email with attachments.",
            full = true,
            code = """
                id: send_email
                namespace: company.team

                inputs:
                  - id: attachments
                    type: ARRAY
                    itemType: JSON

                tasks:
                  - id: send_email
                    type: io.kestra.plugin.notifications.mail.MailSend
                    from: hello@kestra.io
                    to: hello@kestra.io
                    attachments: {{ inputs.attachments | toJson }}
                """
        ),
        @Example(
            title = "Send an email with an embedded image.",
            full = true,
            code = """
                id: send_email
                namespace: company.team

                inputs:
                  - id: embedded_image_uri
                    type: STRING

                tasks:
                  - id: send_email
                    type: io.kestra.plugin.notifications.mail.MailSend
                    from: hello@kestra.io
                    to: hello@kestra.io
                    embeddedImages:
                      - name: kestra.png
                        uri: "{{ inputs.embedded_image_uri }}"
                        contentType: image/png
                """
        ),
        @Example(
            title = "Export Kestra audit logs to a CSV file and send it by email.",
            full = true,
            code = """
                id: export_audit_logs_csv
                namespace: company.team

                tasks:
                  - id: ship_audit_logs
                    type: "io.kestra.plugin.ee.core.log.AuditLogShipper"
                    lookbackPeriod: P1D
                    logExporters:
                      - id: file
                        type: io.kestra.plugin.ee.core.log.FileLogExporter

                  - id: convert_to_csv
                    type: "io.kestra.plugin.serdes.csv.IonToCsv"
                    from: "{{ outputs.ship_audit_logs.outputs.file.uris | first }}"

                  - id: send_email
                    type: io.kestra.plugin.notifications.mail.MailSend
                    from: hello@kestra.io
                    to: hello@kestra.io
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    host: mail.privateemail.com
                    port: 465 # or 587
                    subject: "Weekly Kestra Audit Logs CSV Export"
                    htmlTextContent: "Weekly Kestra Audit Logs CSV Export"
                    attachments:
                      - name: audit_logs.csv
                        uri: "{{ outputs.convert_to_csv.uri }}"
                        contentType: text/csv

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: 0 10 * * 5
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
    private final Property<TransportStrategy> transportStrategy = Property.ofValue(TransportStrategy.SMTPS);

    @Schema(
        title = "Integer value in milliseconds. Default is 10000 milliseconds, i.e. 10 seconds",
        description = "It controls the maximum timeout value when sending emails."
    )
    @Builder.Default
    private final Property<Integer> sessionTimeout = Property.ofValue(10000);

    /* Mail info */
    @Schema(
        title = "The address of the sender of this email"
    )
    protected Property<String> from;

    @Schema(
        title = "Email address(es) of the recipient(s). Use semicolon as delimiter to provide several email addresses.",
        description = "Note that each email address must be compliant with the RFC2822 format."
    )
    protected Property<String> to;

    @Schema(
        title = "One or more 'Cc' (carbon copy) optional recipient email address. Use semicolon as delimiter to provide several addresses.",
        description = "Note that each email address must be compliant with the RFC2822 format."
    )
    protected Property<String> cc;

    @Schema(
        title = "The optional subject of this email"
    )
    protected Property<String> subject;

    @Schema(
        title = "The optional email message body in HTML text",
        description = "Both text and HTML can be provided; either will be offered to the email client as alternative content. " +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely."
    )
    protected Property<String> htmlTextContent;

    @Schema(
        title = "The optional email message body in plain text",
        description = "Both text and HTML can be provided; either will be offered to the email client as alternative content. " +
            "Email clients that support it, will favor HTML over plain text and ignore the text body completely."
    )
    protected Property<String> plainTextContent;

    @Schema(
        title = "Adds an attachment to the email message",
        description = "The attachment will be shown in the email client as separate files available for download or display. " +
            "Inline if the client supports it (for example, most browsers display PDF's in a popup window).",
        anyOf = {List.class, String.class} // Can be a List<Attachment> or a String like "{{ inputs.attachments | toJson }})"
    )
    private Property<Object> attachments;

    @Schema(
        title = "Adds image data to this email that can be referred to from the email HTML body.",
        description = "The provided images are assumed to be of MIME type png, jpg, or whatever the email client supports as valid image that can be embedded in HTML content.",
        anyOf = {List.class, String.class} // Can be a List<Attachment> or a String like "{{ inputs.attachments | toJson }})"
    )
    private Property<Object> embeddedImages;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Logger logger = runContext.logger();

        logger.debug("Sending an email to {}", to);

        final String htmlContent = runContext.render(this.htmlTextContent).as(String.class).orElse(null);
        final String textContent = runContext.render(this.plainTextContent).as(String.class)
            .orElse("Please view this email in a modern email client");

        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
            .to(runContext.render(to).as(String.class).orElseThrow())
            .from(runContext.render(from).as(String.class).orElseThrow())
            .withSubject(runContext.render(subject).as(String.class).orElse(null))
            .withHTMLText(htmlContent)
            .withPlainText(textContent)
            .withReturnReceiptTo();

        var renderedAttachments = runContext.render(attachments).as(Object.class).orElse("");
        var attachmentsList = getAttachments(renderedAttachments);

        if (!attachmentsList.isEmpty()) {
            builder.withAttachments(this.attachmentResources(attachmentsList, runContext));
        }

        var renderedEmbeddedImages = runContext.render(embeddedImages).as(Object.class).orElse("");
        var embeddedImagesList = getAttachments(renderedEmbeddedImages);

        if (!embeddedImagesList.isEmpty()) {
            builder.withEmbeddedImages(this.attachmentResources(embeddedImagesList, runContext));
        }

        runContext.render(cc).as(String.class).ifPresent(builder::cc);

        Email email = builder.buildEmail();

        try (Mailer mailer = MailerBuilder
            .withSMTPServer(
                runContext.render(this.host).as(String.class).orElse(null),
                runContext.render(this.port).as(Integer.class).orElse(null),
                runContext.render(this.username).as(String.class).orElse(null),
                runContext.render(this.password).as(String.class).orElse(null)
            )
            .withTransportStrategy(runContext.render(transportStrategy).as(TransportStrategy.class).orElse(TransportStrategy.SMTPS))
            .withSessionTimeout(runContext.render(sessionTimeout).as(Integer.class).orElse(10000))
            .buildMailer()) {
            mailer.sendMail(email);
        }

        return null;
    }

    private List<AttachmentResource> attachmentResources(List<Attachment> attachments, RunContext runContext) throws Exception {
        return attachments
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.storage()
                    .getFile(URI.create(runContext.render(attachment.getUri()).as(String.class).orElseThrow()));

                return new AttachmentResource(
                    runContext.render(attachment.getName()).as(String.class).orElseThrow(),
                    new ByteArrayDataSource(inputStream, runContext.render(attachment.getContentType()).as(String.class).orElseThrow())
                );
            }))
            .toList();
    }

    private List<Attachment> getAttachments(Object attachments) throws JsonProcessingException {
        switch (attachments) {
            case null -> {
                return List.of();
            }

            case List<?> list -> {
                if (list.isEmpty()) return List.of();

                if (list.getFirst() instanceof Attachment) {
                    @SuppressWarnings("unchecked")
                    List<Attachment> typed = (List<Attachment>) list;
                    return typed;
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) list;
                    return toAttachments(items);
                }
            }

            case String content -> {
                String trimmed = content.trim();
                if (trimmed.isEmpty()) return List.of();

                if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    return parseJsonAttachmentString(trimmed);
                }

                String innerJson = JacksonMapper.ofJson().readValue(trimmed, String.class);
                return parseJsonAttachmentString(innerJson);
            }
            default -> {
            }
        }

        throw new IllegalArgumentException("The `attachments` attribute must be a String or a List");
    }

    private List<Attachment> parseJsonAttachmentString(String json) throws JsonProcessingException {
        String t = json.trim();
        if (t.startsWith("[")) {
            List<Map<String, Object>> items = JacksonMapper.ofJson().readValue(t, new TypeReference<>() {
            });
            return toAttachments(items);
        } else if (t.startsWith("{")) {
            Map<String, Object> item = JacksonMapper.ofJson().readValue(t, new TypeReference<>() {
            });
            return toAttachments(List.of(item));
        } else {
            return List.of();
        }
    }

    private static List<Attachment> toAttachments(List<Map<String, Object>> items) {
        return items.stream()
            .map(item -> Attachment.builder()
                .name(Property.ofValue((String) item.get("name")))
                .uri(Property.ofValue((String) item.get("uri")))
                .contentType(Property.ofValue((String) item.getOrDefault("contentType", "application/octet-stream")))
                .build())
            .toList();
    }

    @Getter
    @Builder
    public static class Attachment {
        @Schema(
            title = "An attachment URI from Kestra internal storage"
        )
        @NotNull
        private Property<String> uri;

        @Schema(
            title = "The name of the attachment (e.g., 'filename.txt')"
        )
        @NotNull
        private Property<String> name;

        @Schema(
            title = "The media type or MIME (Multipurpose Internet Mail Extensions) type of the resource being sent",
            description = "For example, 'text/plain', 'image/png', 'application/pdf', `text/csv`, etc. " +
                "If not provided, it will default to 'application/octet-stream'."
        )
        @NotNull
        @Builder.Default
        private Property<String> contentType = Property.ofValue("application/octet-stream");
    }
}
