package io.kestra.plugin.notifications.telegram;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send an automated Telegram message from a workflow.",
    description = """
        This task is deprecated since Kestra v1.1.11 and has been replaced by `plugin-telegram (io.kestra.plugin.telegram)`.
        """
)
@Deprecated
public class TelegramSend extends AbstractHttpOptionsTask {
    private static final String TELEGRAMAPI_BASE_URL = "https://api.telegram.org";

    @Schema(title = "Telegram Bot token")
    @NotNull
    protected Property<String> token;

    @Schema(title = "Telegram channel/user ID")
    @NotNull
    protected Property<String> channel;

    @Schema(title = "Message payload")
    protected Property<String> payload;

    @Schema(title = "Telegram Bot parse-Mode", description = "Optional text formatting mode for Telegram messages. Supported values: HTML, Markdown, MarkdownV2.", example = "HTML")
    @Nullable
    protected Property<ParseMode> parseMode;
    @Schema(
        title = "Only to be used when testing locally"
    )
    protected Property<String> endpointOverride;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.endpointOverride).as(String.class).orElse(TELEGRAMAPI_BASE_URL);

        HttpRequest.HttpRequestBuilder requestBuilder = createRequestBuilder(runContext);

        try (HttpClient httpClient = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String destination = runContext.render(this.channel).as(String.class).orElseThrow();
            String apiToken = runContext.render(this.token).as(String.class).orElseThrow();
            String rendered = runContext.render(payload).as(String.class).orElseThrow();
            String parseMode = runContext.render(this.parseMode).as(ParseMode.class).map(ParseMode::getValue).orElse(null);
            TelegramBotApiService.send(httpClient, destination, apiToken, rendered, url, requestBuilder, parseMode);
        }

        return null;
    }

    public enum ParseMode {
        HTML("HTML"),
        MARKDOWNV2("MarkdownV2");

        private final String value;

        ParseMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
