package io.kestra.plugin.notifications.slack;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.runner.memory.MemoryRunner;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

@MicronautTest
class SlackExecutionTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    protected MemoryRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SlackExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws TimeoutException {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "slack",
            null,
            (f, e) -> ImmutableMap.of("url", embeddedServer.getURI().toString())
        );

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(SlackWebController.data, containsString(execution.getId()));
        assertThat(SlackWebController.data, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(SlackWebController.data, containsString("Failed on task `failed`"));
    }
}
