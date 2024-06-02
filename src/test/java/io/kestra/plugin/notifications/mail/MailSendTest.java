package io.kestra.plugin.notifications.mail;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
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
            Charsets.UTF_8
        ).read();

        textTemplate = Files.asCharSource(
            new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                .getResource("text-template.hbs.peb"))
                .toURI()),
            Charsets.UTF_8
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
            "customMessage", "myCustomMessage"
        ));
    }

    @Test
    @DisplayName("Send email with html and plain text contents")
    void sendEmail() throws Exception {
        RunContext runContext = getRunContext();
        URL resource = MailSendTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        MailSend mailSend = MailSend.builder()
            .host("localhost")
            .port(greenMail.getSmtp().getPort())
            .from(FROM)
            .to(TO)
            .subject(SUBJECT)
            .htmlTextContent(template)
            .plainTextContent(textTemplate)
            .transportStrategy(TransportStrategy.SMTP)
            .attachments(List.of(MailSend.Attachment.builder()
                .name("application.yml")
                .uri(put.toString())
                .contentType("text/yaml")
                .build())
            )
            .build();

        mailSend.run(runContext);

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();

        assertThat(receivedMessages.length, is(1));

        MimeMessage mimeMessage = receivedMessages[0];
        MimeMultipart content = (MimeMultipart) mimeMessage.getContent();

        assertThat(content.getCount(), is(2));

        MimeBodyPart bodyPart = ((MimeBodyPart) content.getBodyPart(0));
        String body = IOUtils.toString(bodyPart.getInputStream(), Charsets.UTF_8);

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

        MimeBodyPart filePart = ((MimeBodyPart) content.getBodyPart(1));
        String file = IOUtils.toString(filePart.getInputStream(), Charsets.UTF_8);

        assertThat(filePart.getContentType(), is("text/yaml; filename=application.yml; name=application.yml"));
        assertThat(filePart.getFileName(), is("application.yml"));
        assertThat(file.replace("\r", ""), is(IOUtils.toString(storageInterface.get(null, put), Charsets.UTF_8)));
    }

    @Test
    @DisplayName("Send email with exception with an html and plain text contents")
    void testThrowsMailException() {
        RunContext runContext = getRunContext();

        Assertions.assertThrows(MailException.class, () -> {
        MailSend mailSend = MailSend.builder()
                .host("fake-host-unknown.com")
                .port(465)
                .from(FROM)
                .to(TO)
                .subject(SUBJECT)
                .htmlTextContent(template)
                .plainTextContent(textTemplate)
                .transportStrategy(TransportStrategy.SMTP)
                .build();

            mailSend.run(runContext);
        });
    }
}
