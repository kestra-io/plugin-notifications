package io.kestra.plugin.notifications;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.HashMap;
import java.util.Map;

@Controller("/webhook-unit-test")
public class FakeWebhookController {
    public static String data;
    public static Map<String, String> headers = new HashMap<>();

    @Post
    public HttpResponse<String> post(@Body String data) {
        FakeWebhookController.data = data;
        return HttpResponse.ok("ok");
    }

    @Post("/with-headers")
    public HttpResponse<String> postWithHeaders(HttpRequest<?> request, @Body String data) {

        FakeWebhookController.data = data;
        request.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });

        return HttpResponse.ok("ok");
    }
}
