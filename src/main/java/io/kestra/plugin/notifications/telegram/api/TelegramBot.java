package io.kestra.plugin.notifications.telegram.api;


import io.kestra.plugin.notifications.telegram.ErrorSendingMessageException;

import java.io.IOException;

public interface TelegramBot {
    void send(String message) throws IOException, ErrorSendingMessageException;
}
