package io.kestra.plugin.notifications.telegram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToObject;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
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
                .endpointOverride(Property.ofValue(embeddedServer.getURL().toString()))
                .token(Property.ofValue(token))
                .channel(Property.ofValue(channel))
                .payload(Property.ofValue(message))
                .build();
        task.run(runContext);

        assertThat(FakeTelegramController.token, containsString(token));
        assertThat(FakeTelegramController.message, equalToObject(new TelegramBotApiService.TelegramMessage(channel, message, null)));

    }

    @Test
    void run_withParseModeAsHTML_shouldSendTelegram() throws Exception {
        RunContext runContext = runContextFactory.of();

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        String message = "Hello";
        String channel = "channel";
        String token = "token";
        String parseMode = TelegramSend.ParseMode.HTML.getValue();

        TelegramSend task = TelegramSend.builder()
            .endpointOverride(Property.ofValue(embeddedServer.getURL().toString()))
            .token(Property.ofValue(token))
            .channel(Property.ofValue(channel))
            .payload(Property.ofValue(message))
            .parseMode(Property.ofValue(TelegramSend.ParseMode.HTML))
            .build();
        task.run(runContext);

        assertThat(FakeTelegramController.token, containsString(token));
        assertThat(FakeTelegramController.message, equalToObject(new TelegramBotApiService.TelegramMessage(channel, message, parseMode)));

    }

    @Test
    void run_withParseModeAsMarktDownV2_shouldSendTelegram() throws Exception {
        RunContext runContext = runContextFactory.of();

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        String message = "Hello";
        String channel = "channel";
        String token = "token";
        String parseMode = TelegramSend.ParseMode.MARKDOWNV2.getValue();

        TelegramSend task = TelegramSend.builder()
            .endpointOverride(Property.ofValue(embeddedServer.getURL().toString()))
            .token(Property.ofValue(token))
            .channel(Property.ofValue(channel))
            .payload(Property.ofValue(message))
            .parseMode(Property.ofValue(TelegramSend.ParseMode.MARKDOWNV2))
            .build();
        task.run(runContext);

        assertThat(FakeTelegramController.token, containsString(token));
        assertThat(FakeTelegramController.message, equalToObject(new TelegramBotApiService.TelegramMessage(channel, message, parseMode)));

    }
}
