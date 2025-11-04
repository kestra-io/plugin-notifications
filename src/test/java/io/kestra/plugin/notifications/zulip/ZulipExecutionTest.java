package io.kestra.plugin.notifications.zulip;

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
class ZulipExecutionTest extends AbstractNotificationTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ZulipExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(ZulipExecutionTest.class.getClassLoader().getResource("flows/zulip")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "zulip"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data,5000);

        assertThat(receivedData, containsString(failedExecution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("{\"title\":\"Env\",\"value\":\"DEV\",\"short\":true}"));
        assertThat(receivedData, containsString("{\"title\":\"Cloud\",\"value\":\"GCP\",\"short\":true}"));
        assertThat(receivedData, containsString("{\"title\":\"Final task ID\",\"value\":\"failed\",\"short\":true}"));
        assertThat(receivedData, containsString("myCustomMessage"));
    }
}
