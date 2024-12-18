package io.kestra.plugin.notifications.telegram;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;

/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class TelegramExecutionTest {
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
        repositoryLoader.load(Objects.requireNonNull(TelegramExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws TimeoutException, QueueException {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        runnerUtils.runOne(
            null,
                "io.kestra.tests",
                "telegram",
                null,
                (f, e) -> ImmutableMap.of("url", embeddedServer.getURI().toString())
        );

        assertThat(FakeTelegramController.token, comparesEqualTo("token"));
        assertThat(FakeTelegramController.message.chat_id(), comparesEqualTo("channel"));
        assertThat(FakeTelegramController.message.text(), containsString("<io.kestra.tests telegram"));
        assertThat(FakeTelegramController.message.text(), containsString("Failed on task `failed`"));
        assertThat(FakeTelegramController.message.text(), containsString("Final task ID ➛ failed"));
    }
}
