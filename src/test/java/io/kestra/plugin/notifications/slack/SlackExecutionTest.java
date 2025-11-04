package io.kestra.plugin.notifications.slack;

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
import static org.hamcrest.Matchers.not;

@KestraTest
class SlackExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SlackExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(SlackExecutionTest.class.getClassLoader().getResource("flows/slack")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-fails",
            "slack"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data,5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("{\"title\":\"Env\",\"value\":\"DEV\",\"short\":true}"));
        assertThat(receivedData, containsString("{\"title\":\"Cloud\",\"value\":\"GCP\",\"short\":true}"));
        assertThat(receivedData, containsString("{\"title\":\"Final task ID\",\"value\":\"failed\",\"short\":true}"));
        assertThat(receivedData, containsString("myCustomMessage"));
    }

    @Test
    void flow_successfullFlowShowLastTaskId() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "slack-successful"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data,5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, not(containsString("Failed on task `success`")));
        assertThat(receivedData, containsString("{\"title\":\"Final task ID\",\"value\":\"success\",\"short\":true}"));
    }
}
