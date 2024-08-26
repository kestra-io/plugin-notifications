package io.kestra.plugin.notifications.zulip;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class ZulipIncomingWebhookTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of(
            "blocks", Arrays.asList(
                ImmutableMap.of(
                    "text", "A message *with some bold text* and _some italicized text_.",
                    "fields", Arrays.asList("*Priority*", "*Type*", "`High`", "`Unit Test`")
                ),
                ImmutableMap.of(
                    "text", "his is a mrkdwn section block :ghost: *this is bold*, and ~this is crossed out~, and <https://google.com|this is a link>",
                    "fields", Arrays.asList("*Priority*", "*Type*", "`Low`", "`Unit Test`")
                )
            )
        ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        ZulipIncomingWebhook task = ZulipIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(ZulipIncomingWebhookTest.class.getClassLoader()
                        .getResource("zulip.peb"))
                        .toURI()),
                    Charsets.UTF_8
                ).read()
            )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

}
