package io.kestra.plugin.notifications.sendgrid;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest
class SendGridMailExecutionTest {
    @Inject
    protected StandAloneRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SendGridMailExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void testFlow() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sendgrid");
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
