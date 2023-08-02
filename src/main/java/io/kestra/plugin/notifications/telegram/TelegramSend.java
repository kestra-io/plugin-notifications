package io.kestra.plugin.notifications.telegram;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.client.netty.NettyHttpClientFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TelegramSend extends Task implements RunnableTask<VoidOutput> {
    private static final String TELEGRAMAPI_BASE_URL = "https://api.telegram.org";
    private static final NettyHttpClientFactory FACTORY = new NettyHttpClientFactory();

    @Schema(title = "Telegram Bot token")
    @PluginProperty(dynamic = true)
    @NotNull
    protected String token;

    @Schema(title = "Telegram channel/user ID")
    @PluginProperty(dynamic = true)
    @NotNull
    protected String channel;

    @Schema(title = "Message payload")
    @PluginProperty(dynamic = true)
    @NotNull
    protected String payload;

    @Schema(
            title = "Only to be used when testing locally",
    )
    @PluginProperty(dynamic = false)
    protected String endpointOverride;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(Objects.requireNonNullElse(this.endpointOverride, TELEGRAMAPI_BASE_URL));
        try (DefaultHttpClient httpClient = new DefaultHttpClient(URI.create(url))) {
            String destination = runContext.render(this.channel);
            String apiToken = runContext.render(this.token);
            String rendered = runContext.render(payload);
            TelegramBotApiService.send(httpClient, destination, apiToken, rendered);
        }

        return null;
    }
}
