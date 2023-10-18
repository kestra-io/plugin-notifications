package io.kestra.plugin.notifications.sendgrid;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
public class SendGridMailSendTest {

    private final String from = "from@mail.com";
    private final String to = "to@mail.com";
    private final String subject = "Mail subject";
    private static String template = null;

    @Inject
    StorageInterface storageInterface;

    @BeforeAll
    public static void setup() throws Exception {

        template = Files.asCharSource(
            new File(Objects.requireNonNull(SendGridMailExecution.class.getClassLoader()
                .getResource("sendgrid-mail-template.hbs.peb"))
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
                "flowId", "sendgrid",
                "namespace", "org.test",
                "state", ImmutableMap.of(
                    "current", "SUCCESS"
                )
            ),
            "duration", Duration.ofMillis(123456),
            "flow", ImmutableMap.of(
                "id", "sendgrid"
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
        URL resource = SendGridMailSendTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        SendGridMailSend mailSend = SendGridMailSend.builder()
            .from(from)
            .to(List.of(to))
            .subject(subject)
            .htmlTextContent(template)
            .attachments(List.of(SendGridMailSend.Attachment.builder()
                .name("application.yml")
                .uri(put.toString())
                .contentType("text/yaml")
                .build())
            )
            .build();

        SendGridMailSend.Output output = mailSend.run(runContext);

        assertThat(output.getStatusCode(), is(200));

        String body = IOUtils.toString(output.getBody().getBytes(), String.valueOf(Charsets.UTF_8));

        assertThat(body, containsString("<strong>Namespace :</strong> or=\r\ng.test"));
        assertThat(body, containsString("<strong>Flow :</strong> sendgrid"));
        assertThat(body, containsString("<strong>Execution :</strong> #a=\r\nBcDeFgH"));
        assertThat(body, containsString("<strong>Status :</strong> SUCCE=\r\nSS"));
        assertThat(body, containsString("<strong>Env :</strong> dev"));
        assertThat(body, containsString("myCustomMessage"));
    }
}
