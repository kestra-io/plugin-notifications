package io.kestra.plugin.notifications.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.micronaut.http.HttpStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.Objects;

public class TelegramBotApiService {

    public static void send(HttpClient client, String destinationId, String apiToken, String message, String url, HttpRequest.HttpRequestBuilder requestBuilder, String parseMode) throws ErrorSendingMessageException {

        TelegramMessage payload = new TelegramMessage(destinationId, message, parseMode);

        String uri = url+ "/bot{token}/sendMessage".replace("{token}", apiToken);

        requestBuilder
            .addHeader("Content-Type", "application/json")
            .uri(URI.create(uri))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.builder()
                .content(payload)
                .build());

        HttpRequest request = requestBuilder.build();

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

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("chat_id")
        private String chatId;

        private String text;

        @JsonProperty("parse_mode")
        private String parseMode;

        @JsonProperty("message_id")
        private Integer messageId;

        public TelegramMessage(String chatId, String text, String parseMode) {
            this.chatId = chatId;
            this.text = text;
            this.parseMode = parseMode;
        }
    }

    public static class ErrorSendingMessageException extends Exception {
        public final HttpResponse.Status httpStatus;

        public ErrorSendingMessageException(HttpResponse.Status httpStatus, Throwable e) {
            super(String.format("Unable to send Telegram message: %s ", httpStatus), e);
            this.httpStatus = httpStatus;
        }
    }
}
