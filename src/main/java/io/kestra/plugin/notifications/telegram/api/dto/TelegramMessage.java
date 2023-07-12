package io.kestra.plugin.notifications.telegram.api.dto;

public record TelegramMessage(String chat_id, String text) {
}
