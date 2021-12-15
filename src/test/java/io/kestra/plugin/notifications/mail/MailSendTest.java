package io.kestra.plugin.notifications.mail;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import org.simplejavamail.MailException;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
public class MailSendTest {
    static Wiser wiser = new Wiser();
    private static final int WISER_PORT = 2500;
    private static final String WISER_HOST = "localhost";
    private final String from = "from@mail.com";
    private final String to = "to@mail.com";
    private final String subject = "Mail subject";
    private static String template = null;

    @Inject
    StorageInterface storageInterface;

    @BeforeAll
    public static void setup() throws Exception {
        wiser.setPort(WISER_PORT);
        wiser.start();

        template = Files.asCharSource(
            new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                .getResource("mail-template.hbs.peb"))
                .toURI()),
            Charsets.UTF_8
        ).read();
    }

    @AfterAll
    public static void tearDown() {
        wiser.stop();
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
    void sendEmail() throws Exception {
        RunContext runContext = getRunContext();
        URL resource = MailSendTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        MailSend mailSend = MailSend.builder()
            .host(WISER_HOST)
            .port(WISER_PORT)
            .from(from)
            .to(to)
            .subject(subject)
            .htmlTextContent(template)
            .transportStrategy(TransportStrategy.SMTP)
            .attachments(List.of(MailSend.Attachment.builder()
                .name("application.yml")
                .uri(put.toString())
                .contentType("text/yaml")
                .build())
            )
            .build();

        mailSend.run(runContext);

        assertThat(wiser.getMessages(), hasSize(1));

        WiserMessage wiserMessage = wiser.getMessages().get(0);
        MimeMessage mimeMessage = wiserMessage.getMimeMessage();
        MimeMultipart content = (MimeMultipart) mimeMessage.getContent();

        assertThat(content.getCount(), is(2));

        MimeBodyPart bodyPart = ((MimeBodyPart) content.getBodyPart(0));
        String body = IOUtils.toString(bodyPart.getInputStream(), Charsets.UTF_8);

        assertThat(wiserMessage.getEnvelopeSender(), is(from));
        assertThat(wiserMessage.getEnvelopeReceiver(), is(to));
        assertThat(mimeMessage.getSubject(), is(subject));
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
        assertThat(file.replace("\r", ""), is(IOUtils.toString(storageInterface.get(put), Charsets.UTF_8)));
    }

    @Test
    void testThrowsMailException() {
        RunContext runContext = getRunContext();

        Assertions.assertThrows(MailException.class, () -> {
            MailSend mailSend = MailSend.builder()
                .host("fake-host-unknown.com")
                .port(465)
                .from(from)
                .to(to)
                .subject(subject)
                .htmlTextContent(template)
                .transportStrategy(TransportStrategy.SMTP)
                .build();

            mailSend.run(runContext);
        });
    }
}
