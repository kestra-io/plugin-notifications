package io.kestra.plugin.notifications.mail;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.simplejavamail.MailException;
import org.simplejavamail.api.mailer.config.TransportStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class MailSendTest {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final String FROM = "from@mail.com";
    private static final String TO = "to@mail.com";
    private static final String SUBJECT = "Mail subject";
    private static String template = null;
    private static String textTemplate = null;

    @Inject
    StorageInterface storageInterface;

    @BeforeAll
    public static void setup() throws Exception {

        template = Files.asCharSource(
            new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                    .getResource("mail-template.hbs.peb"))
                .toURI()),
            StandardCharsets.UTF_8
        ).read();

        textTemplate = Files.asCharSource(
            new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                    .getResource("text-template.hbs.peb"))
                .toURI()),
            StandardCharsets.UTF_8
        ).read();
    }

    @Inject
    private RunContextFactory runContextFactory;

    private RunContext getRunContext() {
        return runContextFactory.of(Map.of(
            "firstFailed", false,
            "execution", ImmutableMap.of(
                "id", "#aBcDeFgH",
                "flowId", "mail",
                "namespace", "org.test",
                "state", ImmutableMap.of(
                    "current", "SUCCESS"
                )
            ),
            "duration", Duration.ofMillis(123456),
            "flow", ImmutableMap.of(
                "id", "mail"
            ),
            "link", "http://todo.com",
            "customFields", ImmutableMap.of(
                "Env", "dev"
            ),
            "customMessage", "myCustomMessage",
            "attachments", """
                    [
                        {
                            "name": "application-test.yml",
                            "uri": "kestra:///file/storage/get.yml",
                            "contentType": "text/yaml"
                        }
                    ]"""
        ));
    }

    @Test
    @DisplayName("Send email with html and plain text contents")
    void sendEmail() throws Exception {
        RunContext runContext = getRunContext();
        String attachmentFilename = "application-test.yml";
        URL attachmentResource = MailSendTest.class.getClassLoader().getResource(attachmentFilename);
        URI putAttachment = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(attachmentResource).getFile())
        );
        String embeddedImageFilename = "kestra.png";
        URL embeddedImageResource = MailSendTest.class.getClassLoader().getResource(embeddedImageFilename);
        URI putEmbeddedImage = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/get-embedded-image.yml"),
            new FileInputStream(Objects.requireNonNull(embeddedImageResource).getFile())
        );

        MailSend mailSend = MailSend.builder()
            .host(Property.ofValue("localhost"))
            .port(Property.ofValue(greenMail.getSmtp().getPort()))
            .from(Property.ofValue(FROM))
            .to(Property.ofValue(TO))
            .subject(Property.ofValue(SUBJECT))
            .htmlTextContent(Property.ofExpression(template))
            .plainTextContent(Property.ofValue(textTemplate))
            .transportStrategy(Property.ofValue(TransportStrategy.SMTP))
            .attachments(Property.ofExpression("{{ attachments | toJson }}"))
            .embeddedImages(Property.ofValue(
                    List.of(MailSend.Attachment.builder()
                        .name(Property.ofValue(embeddedImageFilename))
                        .uri(Property.ofValue(putEmbeddedImage.toString()))
                        .contentType(Property.ofValue("image/png"))
                        .build()
                    )
                )
            )
            .build();

        mailSend.run(runContext);

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();

        assertThat(receivedMessages.length, is(1));

        MimeMessage mimeMessage = receivedMessages[0];
        MimeMultipart content = (MimeMultipart) mimeMessage.getContent();

        assertThat(content.getCount(), is(2));

        MimeBodyPart bodyPart = ((MimeBodyPart) content.getBodyPart(0));
        String body = IOUtils.toString(bodyPart.getInputStream(), StandardCharsets.UTF_8);

        assertThat(mimeMessage.getFrom()[0].toString(), is(FROM));
        assertThat(((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress(), is(TO));
        assertThat(mimeMessage.getSubject(), is(SUBJECT));
        assertThat(body, containsString("Please view this email in a modern email client"));
        assertThat(body, containsString("<strong>Namespace :</strong> or=\r\ng.test"));
        assertThat(body, containsString("<strong>Flow :</strong> mail"));
        assertThat(body, containsString("<strong>Execution :</strong> #a=\r\nBcDeFgH"));
        assertThat(body, containsString("<strong>Status :</strong> SUCCE=\r\nSS"));
        assertThat(body, containsString("<strong>Env :</strong> dev"));
        assertThat(body, containsString("myCustomMessage"));
        assertThat(body, containsString("Content-Type: image/png; filename=kestra.png; name=kestra.png"));

        MimeBodyPart filePart = ((MimeBodyPart) content.getBodyPart(1));
        String file = IOUtils.toString(filePart.getInputStream(), StandardCharsets.UTF_8);

        assertThat(filePart.getContentType(), is("text/yaml; filename=" + attachmentFilename + "; name=" + attachmentFilename));
        assertThat(filePart.getFileName(), is(attachmentFilename));
        assertThat(file.replace("\r", ""), is(IOUtils.toString(storageInterface.get(MAIN_TENANT, null, putAttachment), StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Send email with exception with an html and plain text contents")
    void testThrowsMailException() {
        RunContext runContext = getRunContext();

        Assertions.assertThrows(MailException.class, () -> {
            MailSend mailSend = MailSend.builder()
                .host(Property.ofValue("fake-host-unknown.com"))
                .port(Property.ofValue(465))
                .from(Property.ofValue(FROM))
                .to(Property.ofValue(TO))
                .subject(Property.ofValue(SUBJECT))
                .htmlTextContent(Property.ofExpression(template))
                .plainTextContent(Property.ofValue(textTemplate))
                .transportStrategy(Property.ofValue(TransportStrategy.SMTP))
                .build();

            mailSend.run(runContext);
        });
    }

    @Test
    void sendsEmailWithCsvAttachment_fromYamlList() throws Exception {
        RunContext runContext = getRunContext();

        String src = "application-test.yml";
        URL res = MailSendTest.class.getClassLoader().getResource(src);
        URI putDataset = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/products.csv"),
            new FileInputStream(Objects.requireNonNull(res).getFile())
        );

        List<Map<String, Object>> attachments = List.of(
            Map.of(
                "name", "data.csv",
                "uri", putDataset.toString(),
                "contentType", "text/csv"
            )
        );

        MailSend mailSend = MailSend.builder()
            .host(Property.ofValue("localhost"))
            .port(Property.ofValue(greenMail.getSmtp().getPort()))
            .from(Property.ofValue(FROM))
            .to(Property.ofValue(TO))
            .subject(Property.ofValue(SUBJECT))
            .htmlTextContent(Property.ofValue("find attached your dataset as a CSV file"))
            .plainTextContent(Property.ofValue(textTemplate))
            .transportStrategy(Property.ofValue(TransportStrategy.SMTP))
            .attachments(Property.ofValue(attachments))
            .build();

        mailSend.run(runContext);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received.length, is(1));

        MimeMessage mimeMessage = received[0];
        MimeMultipart content = (MimeMultipart) mimeMessage.getContent();

        assertThat(content.getCount(), is(2));

        MimeBodyPart bodyPart = (MimeBodyPart) content.getBodyPart(0);
        String body = IOUtils.toString(bodyPart.getInputStream(), StandardCharsets.UTF_8);

        assertThat(mimeMessage.getFrom()[0].toString(), is(FROM));
        assertThat(((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress(), is(TO));
        assertThat(mimeMessage.getSubject(), is(SUBJECT));
        assertThat(body, containsString("find attached your dataset as a CSV file"));

        MimeBodyPart filePart = (MimeBodyPart) content.getBodyPart(1);
        String file = IOUtils.toString(filePart.getInputStream(), StandardCharsets.UTF_8);

        assertThat(filePart.getContentType(), containsString("text/csv"));
        assertThat(filePart.getFileName(), is("data.csv"));
        assertThat(file.replace("\r", ""),
            is(IOUtils.toString(storageInterface.get(MAIN_TENANT, null, putDataset), StandardCharsets.UTF_8)));
    }
}
