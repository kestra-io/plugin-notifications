package io.kestra.plugin.notifications.squadcast;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
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
import static org.hamcrest.Matchers.containsString;

@KestraTest
class SquadcastExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SquadcastExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(SquadcastExecutionTest.class.getClassLoader().getResource("flows/squadcast")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "squadcast"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data,5000);

        assertThat(receivedData, containsString(failedExecution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("[io.kestra.tests] main-flow-that-fails"));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("Kestra Squadcast alert"));
    }
}
