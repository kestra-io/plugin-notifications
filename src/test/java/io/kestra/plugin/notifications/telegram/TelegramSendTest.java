package io.kestra.plugin.notifications.telegram;

import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToObject;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@MicronautTest
class TelegramSendTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        String message = "Hello";
        String channel = "channel";
        String token = "token";

        TelegramSend task = TelegramSend.builder()
                .endpointOverride(embeddedServer.getURL().toString())
                .token(token)
                .channel(channel)
                .payload(message)
                .build();
        task.run(runContext);

        assertThat(FakeTelegramController.token, containsString(token));
        assertThat(FakeTelegramController.message, equalToObject(new TelegramBotApiService.TelegramMessage(channel, message)));

    }
}
