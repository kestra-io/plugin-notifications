package io.kestra.plugin.notifications.telegram;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.telegram.api.TelegramBot;
import io.kestra.plugin.notifications.telegram.api.TelegramBotImpl;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.client.netty.NettyHttpClientFactory;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
        title = "Send input as a telegram message"
)
public class TelegramSend extends Task implements RunnableTask<VoidOutput> {
    private static final String TELEGRAMAPI_BASE_URL = "https://api.telegram.org";
    private static final NettyHttpClientFactory FACTORY = new NettyHttpClientFactory();
    @Schema(
            title = "Telegram Bot token",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @PluginProperty(dynamic = true)
    protected String token;
    @Schema(
            title = "Telegram channel/user ID",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @PluginProperty(dynamic = true)
    protected String channel;
    @Schema(
            title = "Message payload",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @PluginProperty(dynamic = true)
    protected String payload;
    @Schema(
            title = "Only to be used when testing locally",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @PluginProperty(dynamic = false)
    protected String endpointOverride;

    protected HttpClient client(RunContext runContext, String base) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        MediaTypeCodecRegistry mediaTypeCodecRegistry = runContext.getApplicationContext().getBean(MediaTypeCodecRegistry.class);

        DefaultHttpClient client = (DefaultHttpClient) FACTORY.createClient(URI.create(base).toURL(), new DefaultHttpClientConfiguration());
        client.setMediaTypeCodecRegistry(mediaTypeCodecRegistry);

        return client;
    }

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        TelegramBot client = TelegramBotImpl.builder()
                .logger(logger)
                .destinationId(runContext.render(this.channel))
                .apiToken(runContext.render(this.token))
                .client(client(
                        runContext,
                        runContext.render(Objects.requireNonNullElse(this.endpointOverride, TELEGRAMAPI_BASE_URL))
                )).build();

        String rendered = runContext.render(payload);
        logger.debug("sending {}", rendered);
        client.send(rendered);

        return null;
    }
}
