package io.kestra.plugin.notifications.sendgrid;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.plugin.notifications.AbstractNotificationTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest
class SendGridMailExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SendGridMailExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(SendGridMailExecutionTest.class.getClassLoader().getResource("flows/sendgrid")));
        this.runner.run();
    }

    @Test
    void testFlow() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-fails",
            "sendgrid"
        );

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }
}
