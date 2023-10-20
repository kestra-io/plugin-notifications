package io.kestra.plugin.notifications.sentry;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@MicronautTest
public class SentryAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SentryAlert task = SentryAlert.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SentryAlertTest.class.getClassLoader()
                            .getResource("sentry.peb"))
                        .toURI()),
                    Charsets.UTF_8
                                  ).read()
                    )
            .bearerAuth(UUID.randomUUID().toString())
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
