package io.kestra.plugin.notifications.telegram;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.micronaut.http.HttpStatus;

import java.net.URI;
import java.util.Objects;

public class TelegramBotApiService {

    public static void send(HttpClient client, String destinationId, String apiToken, String message,String url) throws ErrorSendingMessageException {

        TelegramMessage payload = new TelegramMessage(destinationId, message);

        String uri = url+ "/bot{token}/sendMessage".replace("{token}", apiToken);
        HttpRequest request = HttpRequest.builder()
            .addHeader("Content-Type", "application/json")
            .uri(URI.create(uri))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.builder()
                .content(payload)
                .build())
            .build();

        try {
            HttpResponse<TelegramBotApiResponse> exchange = client.request(request, TelegramBotApiService.TelegramBotApiResponse.class);

            if (exchange.getStatus().getCode() != HttpStatus.OK.getCode()
                    || exchange.getBody() == null
                    || !Objects.requireNonNull(exchange.getBody()).ok()) {
                throw new ErrorSendingMessageException(exchange.getStatus(), null);
            }
        } catch (HttpClientResponseException e) {
            throw new ErrorSendingMessageException(Objects.requireNonNull(e.getResponse()).getStatus(), e);
        } catch (IllegalVariableEvaluationException | HttpClientException e) {
            throw new RuntimeException(e);
        }
    }

    public record TelegramBotApiResponse(boolean ok, TelegramMessage result) {
    }

    public record TelegramMessage(String chat_id, String text) {
    }

    public static class ErrorSendingMessageException extends Exception {
        public final HttpResponse.Status httpStatus;

        public ErrorSendingMessageException(HttpResponse.Status httpStatus, Throwable e) {
            super(String.format("Unable to send Telegram message: %s ", httpStatus), e);
            this.httpStatus = httpStatus;
        }
    }
}
