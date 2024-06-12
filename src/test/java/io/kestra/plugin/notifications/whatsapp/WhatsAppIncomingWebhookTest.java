package io.kestra.plugin.notifications.whatsapp;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.kestra.plugin.notifications.discord.DiscordIncomingWebhook;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
public class WhatsAppIncomingWebhookTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "textBody", "WhatsApp test webhook notification. A message *with some bold text* and _some italicized text_.",
            "whatsAppIds", List.of("someId", "secondId")
                                                           ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        WhatsAppIncomingWebhook task = WhatsAppIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(WhatsAppIncomingWebhookTest.class.getClassLoader()
                            .getResource("whatsapp.peb"))
                        .toURI()),
                    Charsets.UTF_8
                                  ).read()
                    )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
