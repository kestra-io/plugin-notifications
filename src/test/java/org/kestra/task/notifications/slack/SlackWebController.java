package org.kestra.task.notifications.slack;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller("/slack-test-unit")
public class SlackWebController {
    public static String data;

    @Post
    public HttpResponse<String> post(@Body String data) {
        SlackWebController.data = data;
        return HttpResponse.ok("ok");
    }
}
