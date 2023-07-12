package io.kestra.plugin.notifications.telegram;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller()
public class FakeTelegramController {

    public static String token;
    public static TelegramBotApiService.TelegramMessage message;

    @Post("/bot{token}/sendMessage")
    public HttpResponse<TelegramBotApiService.TelegramBotApiResponse> post(String token, @Body TelegramBotApiService.TelegramMessage message) {
        FakeTelegramController.token = token;
        FakeTelegramController.message = message;
        return HttpResponse.ok(new TelegramBotApiService.TelegramBotApiResponse(true, message));
    }
}