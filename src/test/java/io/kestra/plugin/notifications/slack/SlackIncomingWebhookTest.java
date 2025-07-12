package io.kestra.plugin.notifications.slack;


import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.kestra.plugin.notifications.FakeWebhookController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class SlackIncomingWebhookTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of(
            "block",
                ImmutableMap.of(
                    "text", "A message *with some bold text* and _some italicized text_. And specials characters ➛➛➛, his is a mrkdwn section block :ghost: *this is bold*, and ~this is crossed out~, and <https://google.com|this is a link>",
                    "field", Arrays.asList("*Priority*", "*Type*", "`High`", "`Unit Test`")
                )
        ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(new Property<>(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SlackIncomingWebhookTest.class.getClassLoader()
                        .getResource("slack.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                ).read())
            )
            .build();

        task.run(runContext);

        assertThatCode(() -> JacksonMapper.ofJson().readTree(FakeWebhookController.data))
            .withFailMessage("we should send a valid JSON to Slack API")
            .doesNotThrowAnyException();
        assertThat(FakeWebhookController.data).contains("ge *with some bold text* an");
        assertThat(FakeWebhookController.data).contains("And specials characters ➛➛➛");
    }

    @Test
    void runWithHeaders() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of(
            "channel", "#vacation",
            "block",
            ImmutableMap.of(
                "text", "Testing with custom headers",
                "field", Arrays.asList("*Priority*", "*Type*", "`High`", "`Unit Test`")
            ),
            "demoApiKey", "demo"
            ));

        Map<String, String> headers = new HashMap<>();
        headers.put("demo-api-key", "{{demoApiKey}}");

        AbstractHttpOptionsTask.RequestOptions options = AbstractHttpOptionsTask.RequestOptions.builder()
            .headers(new Property<>(headers))
            .build();

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test/with-headers")
            .options(options)
            .payload(new Property<>(
                Files.asCharSource(
                    new File(Objects.requireNonNull(SlackIncomingWebhookTest.class.getClassLoader()
                            .getResource("slack.peb"))
                        .toURI()),
                    StandardCharsets.UTF_8
                ).read())
            )
            .build();

        task.run(runContext);

        assertThatCode(() -> JacksonMapper.ofJson().readTree(FakeWebhookController.data))
            .withFailMessage("we should send a valid JSON to Slack API")
            .doesNotThrowAnyException();

        assertThat(FakeWebhookController.headers).containsEntry("demo-api-key", "demo");
    }

    @Test
    void shouldSendMessageTextWithMarkdown() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        String messageText =
            "Breaking \"news\":\n" +
                "*This is bold* _italic_ ~strike~\n" +
                "<https://example.com|Link>\n" +
                "Line2\n" +
                "Emoji: :tada:";

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .messageText(Property.ofValue(messageText))
            .build();

        task.run(runContext);

        assertThatCode(() -> JacksonMapper.ofJson().readTree(FakeWebhookController.data))
            .doesNotThrowAnyException();

        assertThat(FakeWebhookController.data).contains("*This is bold*");
        assertThat(FakeWebhookController.data).contains("\\\"news\\\"");
        assertThat(FakeWebhookController.data).contains("<https://example.com|Link>");
        assertThat(FakeWebhookController.data).contains("Line2");
        assertThat(FakeWebhookController.data).contains(":tada:");
    }

    @Test
    void shouldThrowWhenInvalidJsonPayload() {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        String invalidPayload = "Breaking \"news\":\n*This is bold*";

        SlackIncomingWebhook task = SlackIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.ofValue(invalidPayload))
            .build();

        Exception thrown = assertThrows(
            com.fasterxml.jackson.core.JsonParseException.class,
            () -> task.run(runContext),
            "Expected JsonParseException due to invalid payload"
        );

        assertThat(thrown.getMessage()).contains("Unrecognized token");
    }
}
