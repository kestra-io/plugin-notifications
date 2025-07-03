package io.kestra.plugin.notifications.telegram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.plugin.notifications.AbstractNotificationTest;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class TelegramExecutionTest extends AbstractNotificationTest {
    @Inject
    protected StandAloneRunner runner;
    @Inject
    protected RunnerUtils runnerUtils;
    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;
    @Inject
    private ApplicationContext applicationContext;

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

        await()
            .atMost(5, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until(() -> FakeTelegramController.message, notNullValue());

        assertThat(FakeTelegramController.token, comparesEqualTo("token"));
        assertThat(FakeTelegramController.message.getChatId(), comparesEqualTo("channel"));
        assertThat(FakeTelegramController.message.getText(), containsString("<io.kestra.tests main-flow-that-fails"));
        assertThat(FakeTelegramController.message.getText(), containsString("Failed on task `failed`"));
        assertThat(FakeTelegramController.message.getText(), containsString("Final task ID ➛ failed"));
    }
}

