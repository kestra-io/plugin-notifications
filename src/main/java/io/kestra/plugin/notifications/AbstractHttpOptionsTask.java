package io.kestra.plugin.notifications;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractHttpOptionsTask extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Options",
        description = "The options to set to customize the HTTP client"
    )
    @PluginProperty(dynamic = true)
    protected RequestOptions options;

    protected HttpConfiguration httpClientConfigurationWithOptions() throws IllegalVariableEvaluationException {
        HttpConfiguration.HttpConfigurationBuilder configuration = HttpConfiguration.builder();

        if (this.options != null) {

            configuration
                .timeout(TimeoutConfiguration.builder()
                    .connectTimeout(this.options.getConnectTimeout())
                    .readIdleTimeout(this.options.getReadIdleTimeout())
                .build())
                .defaultCharset(this.options.getDefaultCharset());
        }

        return configuration.build();
    }

    @Getter
    @Builder
    public static class RequestOptions {
        @Schema(title = "The time allowed to establish a connection to the server before failing.")
        private final Property<Duration> connectTimeout;

        @Schema(title = "The maximum time allowed for reading data from the server before failing.")
        @Builder.Default
        private final Property<Duration> readTimeout = Property.of(Duration.ofSeconds(10));

        @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
        @Builder.Default
        private final Property<Duration> readIdleTimeout = Property.of(Duration.of(5, ChronoUnit.MINUTES));

        @Schema(title = "The time an idle connection can remain in the client's connection pool before being closed.")
        @Builder.Default
        private final Property<Duration> connectionPoolIdleTimeout = Property.of(Duration.ofSeconds(0));

        @Schema(title = "The maximum content length of the response.")
        @Builder.Default
        private final Property<Integer> maxContentLength = Property.of(1024 * 1024 * 10);

        @Schema(title = "The default charset for the request.")
        @Builder.Default
        private final Property<Charset> defaultCharset = Property.of(StandardCharsets.UTF_8);
    }
}
