package io.kestra.plugin.notifications.zenduty;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

@KestraTest
public class ZendutyExecutionTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    protected StandAloneRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ZendutyExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws TimeoutException, QueueException {
        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "zenduty",
            null,
            (f, e) -> ImmutableMap.of("url", embeddedServer.getURI().toString())
        );

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(FakeWebhookController.data, containsString(execution.getId()));
        assertThat(FakeWebhookController.data, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(FakeWebhookController.data, containsString("changed status to FAILED"));
        assertThat(FakeWebhookController.data, containsString("\"Final task ID\":\"failed\""));
        assertThat(FakeWebhookController.data, containsString("Kestra Zenduty alert"));
    }

}
