package io.kestra.plugin.notifications.sentry;


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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;

@KestraTest
public class SentryAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @DisplayName("Run with deprecated /store endpoint")
    void runWithStoreEndpoint() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("extra", Map.of(
                "title", "Sentry test alert notification to store endpoint",
                "text", "ge *with some bold text* an",
                "service", IdUtils.create()))
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SentryAlert task = SentryAlert.builder()
            .id(IdUtils.create())
            .dsn(embeddedServer.getURI() + "/webhook-unit-test")
            .endpointType(EndpointType.STORE)
            .payload(Property.of(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SentryAlertTest.class.getClassLoader()
                            .getResource("sentry.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                                  ).read()
                    ))
            .build();

        task.run(runContext);

        assertAll(
                "Grouped Assertions of Store Data",
                () -> assertThat(FakeWebhookController.data, containsString("Sentry test alert notification to store endpoint")),
                () -> assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"))
        );
    }

    @Test
    @DisplayName("Run with new /envelope endpoint")
    void runWithEnvelopeEndpoint() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("eventId", "c91832d35bd54bbebc20f3e6b8e84538","extra", Map.of(
                "title", "Sentry test alert notification to envelope endpoint",
                "text", "ge *with some bold text* an",
                "service", IdUtils.create()))
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SentryAlert task = SentryAlert.builder()
                .id(IdUtils.create())
                .dsn(embeddedServer.getURI() + "/webhook-unit-test")
                .endpointType(EndpointType.ENVELOPE)
                .payload(Property.of(
                        Files.asCharSource(
                                new File(Objects.requireNonNull(SentryAlertTest.class.getClassLoader()
                                                .getResource("sentry.peb"))
                                        .toURI()),
                                StandardCharsets.UTF_8
                        ).read())
                )
                .build();

        task.run(runContext);

        assertAll(
                "Grouped Assertions of Envelope Data",
                () -> assertThat(FakeWebhookController.data, containsString("c91832d35bd54bbebc20f3e6b8e84538")),
                () -> assertThat(FakeWebhookController.data, containsString("Sentry test alert notification to envelope endpoint")),
                () -> assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"))
        );
    }

}
