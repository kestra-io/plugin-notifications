package io.kestra.plugin.notifications.telegram.api.dto;

public record TelegramBotApiResponse(boolean ok, TelegramMessage result) {
}
