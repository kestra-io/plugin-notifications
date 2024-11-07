package io.kestra.plugin.notifications.teams;


import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class TeamsIncomingWebhookTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(
            Map.of(
                "activityTitle", "Teams notification",
                "activitySubtitle", "Teams notification from Kestra"
            )
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        TeamsIncomingWebhook task = TeamsIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.of(
                Files.asCharSource(
                    new File(Objects.requireNonNull(TeamsIncomingWebhookTest.class.getClassLoader()
                            .getResource("teams.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                ).read())
            )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("Teams notification from Kestra"));
    }
}