package io.kestra.plugin.notifications.telegram;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.AbstractHttpOptionsTask;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.client.netty.NettyHttpClientFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TelegramSend extends AbstractHttpOptionsTask {
    private static final String TELEGRAMAPI_BASE_URL = "https://api.telegram.org";
    private static final NettyHttpClientFactory FACTORY = new NettyHttpClientFactory();

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

        try (DefaultHttpClient httpClient = new DefaultHttpClient(URI.create(url), super.httpClientConfigurationWithOptions(runContext))) {
            String destination = runContext.render(this.channel).as(String.class).orElseThrow();
            String apiToken = runContext.render(this.token).as(String.class).orElseThrow();
            String rendered = runContext.render(payload).as(String.class).orElseThrow();
            TelegramBotApiService.send(httpClient, destination, apiToken, rendered);
        }

        return null;
    }
}
