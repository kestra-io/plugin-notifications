package io.kestra.plugin.notifications.line;

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
public class LineExecutionTest extends AbstractNotificationTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader
                .load(Objects.requireNonNull(LineExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader
                .load(Objects.requireNonNull(LineExecutionTest.class.getClassLoader().getResource("flows/line")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var execution = runAndCaptureExecution(
                "main-flow-that-fails",
                "line-failure-notification");

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("FAILED"));
        assertThat(receivedData, containsString("Environment: DEV"));
        assertThat(receivedData, containsString("Region: Asia-Pacific"));
        assertThat(receivedData, containsString("LINE_USER_ID_123"));
        assertThat(receivedData, containsString("Production alert"));
    }

    @Test
    void flow_successfulFlowShowLastTaskId() throws Exception {
        var execution = runAndCaptureExecution(
                "main-flow-that-succeeds",
                "line-successful-notification");

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, not(containsString("Failed Task:")));
        assertThat(receivedData, containsString("SUCCESS"));
        assertThat(receivedData, containsString("Environment: DEV"));
        assertThat(receivedData, containsString("Status: SUCCESS"));
        assertThat(receivedData, containsString("LINE_USER_ID_123"));
    }
}