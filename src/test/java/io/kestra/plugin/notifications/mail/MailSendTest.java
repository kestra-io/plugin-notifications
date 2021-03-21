package io.kestra.plugin.notifications.mail;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import net.kemitix.wiser.assertions.WiserAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import org.simplejavamail.MailException;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.subethamail.wiser.Wiser;

import java.io.File;
import java.util.Objects;
import javax.inject.Inject;

@MicronautTest
public class MailSendTest {
    static Wiser wiser = new Wiser();
    private static final int WISER_PORT = 2500;
    private static final String WISER_HOST = "localhost";
    private final String from = "from@mail.com";
    private final String to = "to@mail.com";
    private final String subject = "Mail subject";
    private static String template = null;

    @BeforeAll
    public static void setup() throws Exception {
        wiser.setPort(WISER_PORT);
        wiser.start();

        template = Files.asCharSource(
            new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                .getResource("mail-template.hbs.html"))
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
        return runContextFactory.of(ImmutableMap.of(
            "firstFailed", false,
            "execution", ImmutableMap.of(
                "id", "#aBcDeFgH",
                "flowId", "mail",
                "namespace", "org.test",
                "state", ImmutableMap.of(
                    "current", "SUCCESS"
                )
            ),
            "flow", ImmutableMap.of(
                "id", "mail"
            ),
            "link", "http://todo.com"
        ));
    }

    @Test
    void sendEmail() throws Exception {
        RunContext runContext = getRunContext();

        MailSend mailSend = MailSend.builder()
            .host(WISER_HOST)
            .port(WISER_PORT)
            .from(from)
            .to(to)
            .subject(subject)
            .htmlTextContent(template)
            .transportStrategy(TransportStrategy.SMTP)
            .build();

        mailSend.run(runContext);

        WiserAssertions.assertReceivedMessage(wiser)
            .from(from)
            .to(to)
            .withSubject(subject)
            .withContentContains("Namespace : org.test")
            .withContentContains("Flow : mail")
            .withContentContains("Execution : #aBcDeFgH")
            .withContentContains("Status : SUCCESS");
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
