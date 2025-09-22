package io.kestra.plugin.notifications.x;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.TestRunner;
import io.kestra.plugin.notifications.AbstractNotificationTest;
import io.kestra.plugin.notifications.FakeWebhookController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import io.kestra.core.models.flows.State;

@KestraTest
class XExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(XExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(XExecutionTest.class.getClassLoader().getResource("flows/x")));
        this.runner.run();
    }

    @Test
    void flowExecutionNotification() throws Exception {
        var failedExecution = runnerUtils.runOne(
            "main",
            "company.team",
            "x"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(failedExecution.getId(), notNullValue());
        assertThat(failedExecution.getState().getCurrent(), is(State.Type.FAILED));

        assertThat(receivedData, notNullValue());
        assertThat(receivedData, containsString("text"));
        assertThat(receivedData, containsString("company.team.x"));
        assertThat(receivedData, containsString("#Kestra #Automation"));
        assertThat(receivedData, containsString("RUNNING"));
        assertThat(receivedData, containsString("\"text\":"));
    }
}
