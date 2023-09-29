package io.kestra.plugin.notifications.discord;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@MicronautTest
public class DiscordIncomingWebhookTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "title", "Discord test webhook notification",
            "username", "Some User",
            "content", "A message *with some bold text* and _some italicized text_.",
            "description", "his is a mrkdwn section block :ghost: *this is bold*, and ~this is crossed out~, and <https://google.com|this is a link>"
                                                           ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        DiscordIncomingWebhook task = DiscordIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(DiscordIncomingWebhookTest.class.getClassLoader()
                            .getResource("discord.peb"))
                        .toURI()),
                    Charsets.UTF_8
                                  ).read()
                    )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
