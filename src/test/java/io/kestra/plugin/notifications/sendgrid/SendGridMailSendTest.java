package io.kestra.plugin.notifications.sendgrid;


import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class SendGridMailSendTest {

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
            new File(Objects.requireNonNull(SendGridMailExecution.class.getClassLoader()
                .getResource("sendgrid-mail-template.hbs.peb"))
                .toURI()),
            StandardCharsets.UTF_8
        ).read();

        textTemplate = Files.asCharSource(
            new File(Objects.requireNonNull(SendGridMailExecution.class.getClassLoader()
                .getResource("sendgrid-text-template.hbs.peb"))
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
    @Disabled("Need a SendGrid API key")
    @DisplayName("Send email with html and plain text contents")
    void sendEmail() throws Exception {
        RunContext runContext = getRunContext();
        URL resource = SendGridMailSendTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        SendGridMailSend mailSend = SendGridMailSend.builder()
            .sendgridApiKey("")
            .from(FROM)
            .to(List.of(TO))
            .subject(Property.of(SUBJECT))
            .htmlContent(Property.of(template))
            .textContent(Property.of(textTemplate))
            .attachments(List.of(SendGridMailSend.Attachment.builder()
                .name(Property.of("application.yml"))
                .uri(Property.of(put.toString()))
                .contentType(Property.of("text/yaml"))
                .build())
            )
            .build();

        SendGridMailSend.Output output = mailSend.run(runContext);

        assertThat(output.getStatusCode(), is(200));

        String body = IOUtils.toString(output.getBody().getBytes(), String.valueOf(StandardCharsets.UTF_8));

        assertThat(body, containsString("Please view this email in a modern email client"));
        assertThat(body, containsString("<strong>Namespace :</strong> or=\r\ng.test"));
        assertThat(body, containsString("<strong>Flow :</strong> sendgrid"));
        assertThat(body, containsString("<strong>Execution :</strong> #a=\r\nBcDeFgH"));
        assertThat(body, containsString("<strong>Status :</strong> SUCCE=\r\nSS"));
        assertThat(body, containsString("<strong>Env :</strong> dev"));
        assertThat(body, containsString("myCustomMessage"));
    }
}
