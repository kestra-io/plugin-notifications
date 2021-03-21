package io.kestra.plugin.notifications.slack;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@MicronautTest
class SlackIncomingWebhookTest {
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

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/slack-test-unit")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SlackIncomingWebhookTest.class.getClassLoader()
                        .getResource("slack.hbs"))
                        .toURI()),
                    Charsets.UTF_8
                ).read()
            )
            .build();

        task.run(runContext);

        assertThat(SlackWebController.data, containsString("ge *with some bold text* an"));
    }

}
