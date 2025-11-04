package io.kestra.plugin.notifications.mail;

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
import static org.hamcrest.Matchers.*;

@KestraTest
class MailExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(MailExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(MailExecutionTest.class.getClassLoader().getResource("flows/mail")));
        this.runner.run();
    }

    @Test
    void testFlow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "mail"
        );

        assertThat(failedExecution.getTaskRunList(), hasSize(1));
        assertThat(failedExecution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }
}
