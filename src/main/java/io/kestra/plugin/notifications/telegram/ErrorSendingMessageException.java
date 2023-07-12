package io.kestra.plugin.notifications.telegram;

import io.micronaut.http.HttpStatus;

public class ErrorSendingMessageException extends Exception {
    public final HttpStatus httpStatus;

    public ErrorSendingMessageException(HttpStatus httpStatus, Throwable e) {
        super(String.format("Unable to send Telegram message: %s ", httpStatus), e);
        this.httpStatus = httpStatus;
    }
}
