package io.kestra.plugin.notifications.telegram;

import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
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

    @Schema(
        title = "Only to be used when testing locally"
    )
    protected Property<String> endpointOverride;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.endpointOverride).as(String.class).orElse(TELEGRAMAPI_BASE_URL);

        try (HttpClient httpClient = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String destination = runContext.render(this.channel).as(String.class).orElseThrow();
            String apiToken = runContext.render(this.token).as(String.class).orElseThrow();
            String rendered = runContext.render(payload).as(String.class).orElseThrow();
            TelegramBotApiService.send(httpClient, destination, apiToken, rendered, url);
        }

        return null;
    }
}
