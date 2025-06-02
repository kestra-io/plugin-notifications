package io.kestra.plugin.notifications.pagerduty;


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
public class PagerDutyAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "payloadSummary", "PagerDuty test alert"
                                                           ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        PagerDutyAlert task = PagerDutyAlert.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.ofValue(
                Files.asCharSource(
                    new File(Objects.requireNonNull(PagerDutyAlertTest.class.getClassLoader()
                            .getResource("pagerduty.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                                  ).read()
                    ))
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
