package io.kestra.plugin.notifications.telegram.api;

import io.kestra.plugin.notifications.telegram.ErrorSendingMessageException;
import io.kestra.plugin.notifications.telegram.api.dto.TelegramBotApiResponse;
import io.kestra.plugin.notifications.telegram.api.dto.TelegramMessage;
import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

@Builder
@AllArgsConstructor
public class TelegramBotImpl implements TelegramBot {

    private static final UriTemplate sendMessageUriTemplate = UriTemplate.of("/bot{token}/sendMessage");
    private String apiToken;
    private String destinationId;
    private Logger logger;
    private HttpClient client;

    @Override
    public void send(String message) throws ErrorSendingMessageException {

        TelegramMessage payload = new TelegramMessage(this.destinationId, message);

        String uri = sendMessageUriTemplate.expand(Map.of(
                "token", this.apiToken
        ));

        HttpRequest<TelegramMessage> request = HttpRequest.create(HttpMethod.POST, uri)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .body(payload);

        try {
            HttpResponse<TelegramBotApiResponse> exchange = client.toBlocking().exchange(request, TelegramBotApiResponse.class);
            if (exchange.status() != HttpStatus.OK
                    || exchange.body() == null
                    || !Objects.requireNonNull(exchange.body()).ok()) {
                logger.error("Send failed with status: {} body: {}", exchange.status(), exchange.body());
                throw new ErrorSendingMessageException(exchange.status(), null);
            }
        } catch (HttpClientResponseException e) {
            logger.error("Send failed with status: {} msg: {} body: {}", e.getStatus(), e.getMessage(), e.getResponse().body(), e);
            throw new ErrorSendingMessageException(e.getStatus(), e);
        }
    }
}
