package io.kestra.plugin.notifications.telegram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.utils.Await;
import io.kestra.plugin.notifications.AbstractNotificationTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class TelegramExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;
    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(TelegramExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(TelegramExecutionTest.class.getClassLoader().getResource("flows/telegram")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "telegram"
        );

        try {
           Await.until(
                () -> FakeTelegramController.message,
                Duration.ofMillis(100),
                Duration.ofSeconds(5)
            );
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for FakeTelegramController.message to be set", e);
        }

        assertThat(FakeTelegramController.token, comparesEqualTo("token"));
        assertThat(FakeTelegramController.message.getChatId(), comparesEqualTo("channel"));
        assertThat(FakeTelegramController.message.getText(), containsString("<io.kestra.tests main-flow-that-fails"));
        assertThat(FakeTelegramController.message.getText(), containsString("Failed on task `failed`"));
        assertThat(FakeTelegramController.message.getText(), containsString("Final task ID ➛ failed"));
    }
}

