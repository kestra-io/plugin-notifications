package io.kestra.plugin.notifications.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
                      Subject: {{ trigger.subject }}
                      From: {{ trigger.from }}
                      Date: {{ trigger.date }}

                triggers:
                  - id: gmail_inbox_trigger
                    type: io.kestra.plugin.notifications.mail.MailReceive
                    protocol: IMAP
                    host: imap.gmail.com
                    port: 993
                    username: "{{ secret('GMAIL_USERNAME') }}"
                    password: "{{ secret('GMAIL_PASSWORD') }}"
                    folder: INBOX
                    interval: PT30S
                    ssl: true
                """
        ),
        @Example(
            title = "Monitor POP3 mailbox with custom settings",
            code = """
                triggers:
                  - id: pop3_mail_trigger
                    type: io.kestra.plugin.notifications.mail.MailReceive
                    protocol: POP3
                    host: pop.example.com
                    port: 995
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    interval: PT2M
                    ssl: true
                    trustAllCertificates: false
                """
        ),
        @Example(
            title = "Monitor specific folder with IMAP",
            code = """
                triggers:
                  - id: imap_folder_trigger
                    type: io.kestra.plugin.notifications.mail.MailReceive
                    protocol: IMAP
                    host: mail.example.com
                    port: 993
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    folder: "Important"
                    interval: PT1M
                    ssl: true
                """
        )
    }
)
public class MailReceive extends AbstractTrigger
        implements PollingTriggerInterface, TriggerOutput<MailReceive.Output> {

    public enum Protocol {
        IMAP,
        POP3
    }

    @Schema(title = "Mail server protocol", description = "The protocol to use for connecting to the mail server")
    @Builder.Default
    private final Property<Protocol> protocol = Property.ofValue(Protocol.IMAP);

    @Schema(title = "Mail server host", description = "The hostname or IP address of the mail server")
    @NotNull
    private Property<String> host;

    @Schema(title = "Mail server port", description = "The port number of the mail server. Defaults: IMAP=993 (SSL), 143 (non-SSL); POP3=995 (SSL), 110 (non-SSL)")
    private Property<Integer> port;

    @Schema(title = "Username", description = "The username for authentication")
    @NotNull
    private Property<String> username;

    @Schema(title = "Password", description = "The password for authentication")
    @NotNull
    private Property<String> password;

    @Schema(title = "Mail folder", description = "The mail folder to monitor (IMAP only)")
    @Builder.Default
    private final Property<String> folder = Property.ofValue("INBOX");

    @Schema(title = "Polling interval", description = "How often to check for new emails")
    @Builder.Default
    @PluginProperty
    private final Property<Duration> interval = Property.ofValue(Duration.ofSeconds(60));

    @Schema(title = "Use SSL", description = "Whether to use SSL/TLS encryption")
    @Builder.Default
    private final Property<Boolean> ssl = Property.ofValue(true);

    @Schema(title = "Trust all certificates", description = "Whether to trust all SSL certificates (use with caution)")
    @Builder.Default
    private final Property<Boolean> trustAllCertificates = Property.ofValue(false);

    @Override
    public Duration getInterval() {
        return this.interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();

        String rProtocol = String.valueOf(runContext.render(this.protocol).as(Protocol.class).orElseThrow());
        String rHost = runContext.render(this.host).as(String.class).orElseThrow();
        String rUsername = runContext.render(this.username).as(String.class).orElseThrow();
        String rPassword = runContext.render(this.password).as(String.class).orElseThrow();
        String rFolder = runContext.render(this.folder).as(String.class).orElse("INBOX");
        Boolean rSsl = runContext.render(this.ssl).as(Boolean.class).orElse(true);
        Boolean rTrustAllCertificates = runContext.render(this.trustAllCertificates).as(Boolean.class).orElse(false);

        Integer rPort = runContext.render(this.port).as(Integer.class)
                .orElse(getDefaultPort(Protocol.valueOf(rProtocol), rSsl));

        try {
            List<EmailData> newEmails = fetchNewEmails(runContext, rProtocol, rHost, rPort, rUsername, rPassword,
                    rFolder, rSsl, rTrustAllCertificates, context);

            if (newEmails.isEmpty()) {
                return Optional.empty();
            }

            EmailData latest = newEmails.stream()
                    .max(Comparator.comparing(EmailData::getDate))
                    .orElse(newEmails.getFirst());

            Output output = Output.builder()
                    .subject(latest.getSubject())
                    .from(latest.getFrom())
                    .to(latest.getTo())
                    .cc(latest.getCc())
                    .bcc(latest.getBcc())
                    .date(latest.getDate())
                    .body(latest.getBody())
                    .messageId(latest.getMessageId())
                    .attachments(latest.getAttachments())
                    .newEmailsCount(newEmails.size())
                    .allNewEmails(newEmails)
                    .build();

            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);
            return Optional.of(execution);

        } catch (Exception e) {
            runContext.logger().error("Error checking for new emails", e);
            throw new RuntimeException("Failed to fetch emails: " + e.getMessage(), e);
        }
    }

    private List<EmailData> fetchNewEmails(RunContext runContext, String rProtocol, String rHost, Integer rPort,
            String rUsername, String rPassword, String rFolder, Boolean rSsl, Boolean rTrustAllCertificates,
            TriggerContext context) throws MessagingException, IOException {

        Properties props = setupMailProperties(rProtocol, rHost, rPort, rSsl, rTrustAllCertificates, runContext);

        String protocolName = getProtocolName(rProtocol, rSsl);
        Session session = Session.getInstance(props, null);
        Store store = session.getStore(protocolName);

        try {
            connectToStore(store, rHost, rPort, rUsername, rPassword, runContext);

            return processMessages(store, rFolder, context, runContext);

        } finally {
            if (store.isConnected()) {
                try {
                    store.close();
                } catch (MessagingException ignored) {
                }
            }
        }
    }

    private EmailData parseEmailData(MimeMessage message) throws MessagingException, IOException {
        Date receivedDate = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        ZonedDateTime date = receivedDate != null
                ? ZonedDateTime.ofInstant(receivedDate.toInstant(), ZonedDateTime.now().getZone())
                : ZonedDateTime.now();

        return EmailData.builder()
                .subject(message.getSubject())
                .from(getAddressString(message.getFrom()))
                .to(getAddressList(message.getRecipients(Message.RecipientType.TO)))
                .cc(getAddressList(message.getRecipients(Message.RecipientType.CC)))
                .bcc(getAddressList(message.getRecipients(Message.RecipientType.BCC)))
                .date(date)
                .body(extractTextContent(message))
                .messageId(message.getMessageID())
                .attachments(extractAttachments(message))
                .build();
    }

    private String getAddressString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return ((InternetAddress) addresses[0]).getAddress();
    }

    private List<String> getAddressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(addresses)
                .map(addr -> ((InternetAddress) addr).getAddress())
                .collect(Collectors.toList());
    }

    private String extractTextContent(Message message) throws MessagingException, IOException {
        if (message.isMimeType("text/plain")) {
            return (String) message.getContent();
        } else if (message.isMimeType("text/html")) {
            return (String) message.getContent();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            return extractTextFromMultipart(multipart);
        }
        return "";
    }

    private String extractTextFromMultipart(MimeMultipart multipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = multipart.getCount();

        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                result.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("multipart/*")) {
                result.append(extractTextFromMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }

        return result.toString();
    }

    private List<AttachmentInfo> extractAttachments(Message message) throws MessagingException, IOException {
        List<AttachmentInfo> attachments = new ArrayList<>();

        if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            extractAttachmentsFromMultipart(multipart, attachments);
        }

        return attachments;
    }

    private void extractAttachmentsFromMultipart(MimeMultipart multipart, List<AttachmentInfo> attachments)
            throws MessagingException, IOException {
        int count = multipart.getCount();

        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) ||
                    (bodyPart.getFileName() != null && !bodyPart.getFileName().isEmpty())) {

                AttachmentInfo attachment = AttachmentInfo.builder()
                        .filename(bodyPart.getFileName())
                        .contentType(bodyPart.getContentType())
                        .size(bodyPart.getSize())
                        .build();

                attachments.add(attachment);
            } else if (bodyPart.isMimeType("multipart/*")) {
                extractAttachmentsFromMultipart((MimeMultipart) bodyPart.getContent(), attachments);
            }
        }
    }

    private Integer getDefaultPort(Protocol protocol, Boolean ssl) {
        return switch (protocol) {
            case IMAP -> ssl ? 993 : 143;
            case POP3 -> ssl ? 995 : 110;
        };
    }

    private Properties setupMailProperties(String rProtocol, String rHost, Integer rPort, Boolean rSsl,
            Boolean rTrustAllCertificates, RunContext runContext) {
        Properties props = new Properties();
        String protocolName = getProtocolName(rProtocol, rSsl);

        props.put("mail.store.protocol", protocolName);
        props.put("mail." + protocolName + ".host", rHost);
        props.put("mail." + protocolName + ".port", rPort.toString());
        props.put("mail." + protocolName + ".auth", "true");

        if (rSsl) {
            props.put("mail." + protocolName + ".ssl.enable", "true");
            props.put("mail." + protocolName + ".ssl.protocols", "TLSv1.2");
            if (rHost.contains("gmail.com")) {
                props.put("mail." + protocolName + ".ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail." + protocolName + ".ssl.socketFactory.fallback", "false");
            }
        }

        if (rTrustAllCertificates) {
            props.put("mail." + protocolName + ".ssl.trust", "*");
            props.put("mail." + protocolName + ".ssl.checkserveridentity", "false");
        }

        if (runContext.logger().isDebugEnabled()) {
            props.put("mail.debug", "true");
        }

        return props;
    }

    private String getProtocolName(String rProtocol, Boolean rSsl) {
        if (rProtocol.equals(Protocol.IMAP.toString())) {
            return rSsl ? "imaps" : "imap";
        }
        return rSsl ? "pop3s" : "pop3";
    }

    private void connectToStore(Store store, String rHost, Integer rPort, String rUsername,
            String rPassword, RunContext runContext) throws MessagingException {
        try {
            store.connect(rHost, rPort, rUsername, rPassword);
        } catch (MessagingException e) {
            store.connect(rUsername, rPassword);
        }
        runContext.logger().info("Connected to {}:{}", rHost, rPort);
    }

    private List<EmailData> processMessages(Store store, String rFolder, TriggerContext context,
            RunContext runContext) throws MessagingException, IOException {
        Folder mailFolder = store.getFolder(rFolder);
        mailFolder.open(Folder.READ_ONLY);

        int messageCount = mailFolder.getMessageCount();
        if (messageCount == 0) {
            runContext.logger().info("No messages found in folder: {}", rFolder);
            return Collections.emptyList();
        }

        ZonedDateTime lastCheckTime = context.getNextExecutionDate() != null
                ? context.getNextExecutionDate().minus(this.interval)
                : ZonedDateTime.now().minus(Duration.ofHours(1));

        runContext.logger().info("Checking for emails newer than: {}", lastCheckTime);

        int messagesToCheck = Math.min(messageCount, 10);
        Message[] messages = mailFolder.getMessages(messageCount - messagesToCheck + 1, messageCount);

        runContext.logger().info("Checking {} messages out of {} total", messagesToCheck, messageCount);

        List<EmailData> newEmails = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof MimeMessage mimeMessage) {
                Date receivedDate = message.getReceivedDate() != null ? message.getReceivedDate()
                        : message.getSentDate();

                if (receivedDate != null) {
                    ZonedDateTime messageDate = ZonedDateTime.ofInstant(receivedDate.toInstant(),
                            lastCheckTime.getZone());

                    runContext.logger().debug("Message date: {}, Last check: {}, Is newer: {}",
                            messageDate, lastCheckTime, messageDate.isAfter(lastCheckTime));

                    if (messageDate.isAfter(lastCheckTime)) {
                        EmailData emailData = parseEmailData(mimeMessage);
                        if (emailData != null) {
                            newEmails.add(emailData);
                            runContext.logger().info("New email - Subject: '{}', From: '{}', Body: '{}'",
                                    emailData.getSubject(), emailData.getFrom(),
                                    emailData.getBody().length() > 100 ? emailData.getBody().substring(0, 100) + "..."
                                            : emailData.getBody());
                        }
                    }
                } else {
                    runContext.logger().debug("Message has no received date or sent date.");
                }
            }
        }

        runContext.logger().info("Found {} new emails", newEmails.size());
        return newEmails;
    }

    @Builder
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Email subject")
        private final String subject;

        @Schema(title = "Sender email address")
        private final String from;

        @Schema(title = "Recipient email addresses")
        private final List<String> to;

        @Schema(title = "CC email addresses")
        private final List<String> cc;

        @Schema(title = "BCC email addresses")
        private final List<String> bcc;

        @Schema(title = "Email date")
        private final ZonedDateTime date;

        @Schema(title = "Email body content")
        private final String body;

        @Schema(title = "Message ID")
        private final String messageId;

        @Schema(title = "Email attachments")
        private final List<AttachmentInfo> attachments;

        @Schema(title = "Total number of new emails found")
        private final Integer newEmailsCount;

        @Schema(title = "All new emails found")
        private final List<EmailData> allNewEmails;
    }

    @Builder
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmailData {
        @Schema(title = "Email subject")
        private final String subject;

        @Schema(title = "Sender email address")
        private final String from;

        @Schema(title = "Recipient email addresses")
        private final List<String> to;

        @Schema(title = "CC email addresses")
        private final List<String> cc;

        @Schema(title = "BCC email addresses")
        private final List<String> bcc;

        @Schema(title = "Email date")
        private final ZonedDateTime date;

        @Schema(title = "Email body content")
        private final String body;

        @Schema(title = "Message ID")
        private final String messageId;

        @Schema(title = "Email attachments")
        private final List<AttachmentInfo> attachments;
    }

    @Builder
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachmentInfo {
        @Schema(title = "Attachment filename")
        private final String filename;

        @Schema(title = "Content type")
        private final String contentType;

        @Schema(title = "File size in bytes")
        private final Integer size;
    }
}