package io.kestra.plugin.notifications.telegram;

import io.kestra.plugin.notifications.telegram.api.dto.TelegramBotApiResponse;
import io.kestra.plugin.notifications.telegram.api.dto.TelegramMessage;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller()
public class FakeTelegramController {

    public static String token;
    public static TelegramMessage message;

    @Post("/bot{token}/sendMessage")
    public HttpResponse<TelegramBotApiResponse> post(String token, @Body TelegramMessage message) {
        FakeTelegramController.token = token;
        FakeTelegramController.message = message;
        return HttpResponse.ok(new TelegramBotApiResponse(true, message));
    }
}