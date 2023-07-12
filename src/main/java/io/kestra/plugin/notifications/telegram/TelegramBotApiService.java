package io.kestra.plugin.notifications.telegram;

import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriTemplate;

import java.util.Map;
import java.util.Objects;

public class TelegramBotApiService {
    private static final UriTemplate sendMessageUriTemplate = UriTemplate.of("/bot{token}/sendMessage");

    public static void send(HttpClient client, String destinationId, String apiToken, String message) throws ErrorSendingMessageException {

        TelegramMessage payload = new TelegramMessage(destinationId, message);

        String uri = sendMessageUriTemplate.expand(Map.of(
                "token", apiToken
        ));

        HttpRequest<TelegramMessage> request = HttpRequest.create(HttpMethod.POST, uri)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .body(payload);

        try {
            HttpResponse<TelegramBotApiResponse> exchange = client.toBlocking().exchange(request, TelegramBotApiService.TelegramBotApiResponse.class);
            if (exchange.status() != HttpStatus.OK
                    || exchange.body() == null
                    || !Objects.requireNonNull(exchange.body()).ok()) {
                throw new ErrorSendingMessageException(exchange.status(), null);
            }
        } catch (HttpClientResponseException e) {
            throw new ErrorSendingMessageException(e.getStatus(), e);
        }
    }

    public record TelegramBotApiResponse(boolean ok, TelegramMessage result) {
    }

    public record TelegramMessage(String chat_id, String text) {
    }

    public static class ErrorSendingMessageException extends Exception {
        public final HttpStatus httpStatus;

        public ErrorSendingMessageException(HttpStatus httpStatus, Throwable e) {
            super(String.format("Unable to send Telegram message: %s ", httpStatus), e);
            this.httpStatus = httpStatus;
        }
    }
}
