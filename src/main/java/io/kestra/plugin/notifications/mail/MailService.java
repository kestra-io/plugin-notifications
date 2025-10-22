package io.kestra.plugin.notifications.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

@UtilityClass
public class MailService {

    public enum Protocol {
        IMAP,
        POP3
    }

    @Builder
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Latest email received", description = "The most recent email that triggered this execution")
        private final EmailData latestEmail;

        @Schema(title = "Total number of new emails found")
        private final Integer total;

        @Schema(title = "All new emails found")
        private final List<EmailData> emails;
    }

    @Builder
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmailData implements io.kestra.core.models.tasks.Output {
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

    @Builder
    @Getter
    public static class MailConfiguration {
        public final String protocol;
        public final String host;
        public final Integer port;
        public final String username;
        public final String password;
        public final String folder;
        public final Boolean ssl;
        public final Boolean trustAllCertificates;
        public final Duration interval;
    }

    public static Properties setupMailProperties(String protocol, String host, Integer port, Boolean ssl,
            Boolean trustAllCertificates, RunContext runContext) {
        Properties props = new Properties();
        String protocolName = getProtocolName(protocol, ssl);

        props.put("mail.store.protocol", protocolName);
        props.put("mail." + protocolName + ".host", host);
        props.put("mail." + protocolName + ".port", port.toString());
        props.put("mail." + protocolName + ".auth", "true");

        if (ssl) {
            props.put("mail." + protocolName + ".ssl.enable", "true");
            props.put("mail." + protocolName + ".ssl.protocols", "TLSv1.2");
        }

        if (trustAllCertificates) {
            props.put("mail." + protocolName + ".ssl.trust", "*");
            props.put("mail." + protocolName + ".ssl.checkserveridentity", "false");
        }

        if (runContext.logger().isDebugEnabled()) {
            props.put("mail.debug", "true");
        }

        return props;
    }

    public static String getProtocolName(String protocol, Boolean ssl) {
        if (protocol.equals("IMAP")) {
            return ssl ? "imaps" : "imap";
        }
        return ssl ? "pop3s" : "pop3";
    }

    public static void connectToStore(Store store, String host, Integer port, String username,
            String password, RunContext runContext) throws MessagingException {
        try {
            store.connect(host, port, username, password);
        } catch (MessagingException e) {
            store.connect(username, password);
        }
        runContext.logger().info("Connected to {}:{}", host, port);
    }

    public static Integer getDefaultPort(Protocol protocol, Boolean ssl) {
        return switch (protocol) {
            case IMAP -> ssl ? 993 : 143;
            case POP3 -> ssl ? 995 : 110;
        };
    }

    public static EmailData parseEmailData(MimeMessage message) throws MessagingException, IOException {
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

    private static String getAddressString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return ((InternetAddress) addresses[0]).getAddress();
    }

    private static List<String> getAddressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(addresses)
                .map(addr -> ((InternetAddress) addr).getAddress())
                .toList();
    }

    private static String extractTextContent(Message message) throws MessagingException, IOException {
        if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            return extractTextFromMultipart(multipart);
        }
        return (String) message.getContent();
    }

    private static String extractTextFromMultipart(MimeMultipart multipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = multipart.getCount();

        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (bodyPart.isMimeType("multipart/*")) {
                result.append(extractTextFromMultipart((MimeMultipart) bodyPart.getContent()));
            } else {
                result.append(bodyPart.getContent().toString());
            }
        }

        return result.toString();
    }

    private static List<AttachmentInfo> extractAttachments(Message message) throws MessagingException, IOException {
        List<AttachmentInfo> attachments = new ArrayList<>();

        if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            extractAttachmentsFromMultipart(multipart, attachments);
        }

        return attachments;
    }

    private static void extractAttachmentsFromMultipart(MimeMultipart multipart, List<AttachmentInfo> attachments)
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

    public static List<EmailData> fetchNewEmails(RunContext runContext, String protocol, String host, Integer port,
                                                 String username, String password, String folder, Boolean ssl, Boolean trustAllCertificates,
                                                 ZonedDateTime lastCheckTime) throws MessagingException, IOException {

        Properties props = setupMailProperties(protocol, host, port, ssl, trustAllCertificates, runContext);
        String protocolName = getProtocolName(protocol, ssl);
        Session session = Session.getInstance(props, null);
        Store store = session.getStore(protocolName);

        try {
            connectToStore(store, host, port, username, password, runContext);
            return processMessages(store, folder, lastCheckTime, runContext);
        } finally {
            if (store.isConnected()) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    runContext.logger().warn("Failed to close mail store", e);
                }
            }
        }
    }

    private static List<EmailData> processMessages(Store store, String folder, ZonedDateTime lastCheckTime,
            RunContext runContext) throws MessagingException, IOException {
        List<EmailData> newEmails = new ArrayList<>();
        Folder mailFolder = store.getFolder(folder);
        try{
            mailFolder.open(Folder.READ_ONLY);

            int messageCount = mailFolder.getMessageCount();
            if (messageCount == 0) {
                runContext.logger().info("No messages found in folder: {}", folder);
                return Collections.emptyList();
            }

            runContext.logger().info("Checking for emails newer than: {}", lastCheckTime);

            int messagesToCheck = Math.min(messageCount, 10);
            Message[] messages = mailFolder.getMessages(messageCount - messagesToCheck + 1, messageCount);

            runContext.logger().info("Checking {} messages out of {} total", messagesToCheck, messageCount);

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
    } finally {
            if (mailFolder.isOpen()) {
                try {
                    mailFolder.close(false);
                } catch (MessagingException e) {
                    runContext.logger().warn("Failed to close mail folder", e);
                }
            }
        }

        runContext.logger().info("Found {} new emails", newEmails.size());
        return newEmails;
    }
}