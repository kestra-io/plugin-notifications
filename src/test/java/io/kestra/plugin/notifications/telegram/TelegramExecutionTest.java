package io.kestra.plugin.notifications.telegram;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.runner.memory.MemoryRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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
@MicronautTest
class TelegramExecutionTest {
    @Inject
    protected MemoryRunner runner;
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
    void flow() throws TimeoutException {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Execution execution = runnerUtils.runOne(
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
    }
}
