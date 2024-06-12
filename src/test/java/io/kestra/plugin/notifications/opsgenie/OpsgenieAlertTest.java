package io.kestra.plugin.notifications.opsgenie;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
public class OpsgenieAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "message", "A message *with some bold text* and _some italicized text_.",
            "alias", "Execution alert",
            "description", "Opsgenie test alert notification"
                                                           ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        OpsgenieAlert task = OpsgenieAlert.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(OpsgenieAlertTest.class.getClassLoader()
                            .getResource("opsgenie.peb"))
                        .toURI()),
                    Charsets.UTF_8
                                  ).read()
                    )
            .authorizationToken(UUID.randomUUID().toString())
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
