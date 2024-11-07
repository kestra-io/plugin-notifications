package io.kestra.plugin.notifications.twilio;


import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
public class TwilioAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "body", "Twilio test alert",
            "identity", "SomeUser"
                                                           ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        TwilioAlert task = TwilioAlert.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.of(
                Files.asCharSource(
                    new File(Objects.requireNonNull(TwilioAlertTest.class.getClassLoader()
                            .getResource("twilio.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                                  ).read())
                    )
            .accountSID(IdUtils.create())
            .authToken(UUID.randomUUID().toString())
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("someUserTag"));
    }

}
