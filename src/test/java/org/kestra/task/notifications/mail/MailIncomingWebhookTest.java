package org.kestra.task.notifications.mail;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import net.kemitix.wiser.assertions.WiserAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kestra.core.runners.RunContext;
import org.subethamail.wiser.Wiser;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;

@MicronautTest
public class MailIncomingWebhookTest {

    static Wiser wiser = new Wiser();
    private static final int WISER_PORT = 2500;
    private static final String WISER_HOST = "localhost";

    @BeforeAll
    public static void setup() {
        wiser.setPort(WISER_PORT);
        wiser.start();
    }

    @AfterAll
    public static void tearDown() {
        wiser.stop();
    }

    @Inject
    private ApplicationContext applicationContext;

    @Test
    void sendEmail() throws Exception {

        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of(
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

        String template = Files.asCharSource(
                new File(Objects.requireNonNull(MailExecution.class.getClassLoader()
                        .getResource("mail-template.hbs.html"))
                        .toURI()),
                Charsets.UTF_8
        ).read();

        final String from = "from@mail.com";
        final String to = "to@mail.com";
        final String subject = "Mail subject";

        MailIncomingWebhook mailIncomingWebhook = MailIncomingWebhook.builder().
                host(WISER_HOST)
                .port(WISER_PORT)
                .from(from)
                .to(to)
                .subject(subject)
                .htmlTextContent(template)
                .build();

        mailIncomingWebhook.run(runContext);

        WiserAssertions.assertReceivedMessage(wiser)
                .from(from)
                .to(to)
                .withSubject(subject)
                .withContentContains("Namespace : org.test")
                .withContentContains("Flow : mail")
                .withContentContains("Execution : #aBcDeFgH")
                .withContentContains("Status : SUCCESS");
    }
}
