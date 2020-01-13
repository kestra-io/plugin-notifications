package org.kestra.task.notifications.mail;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.serializers.JacksonMapper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MailExecution extends MailSend {
    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        String htmlTextTemplate = Files.asCharSource(
            new File(Objects.requireNonNull(this.getClass().getClassLoader()
                .getResource("mail-template.hbs.html"))
                .toURI()),
            Charsets.UTF_8
        ).read();

        @SuppressWarnings("unchecked")
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        Map<String, Object> renderMap = new HashMap<>();
        renderMap.put("duration", execution.getState().humanDuration());
        renderMap.put("startDate", execution.getState().getStartDate());
        // FIXME
        renderMap.put("link", "https://todo.com");

        execution
            .findFirstByState(State.Type.FAILED)
            .ifPresentOrElse(
                taskRun -> renderMap.put("firstFailed", taskRun),
                () -> renderMap.put("firstFailed", false)
            );

        this.htmlTextContent = runContext.render(htmlTextTemplate, renderMap);

        return super.run(runContext);
    }
}
