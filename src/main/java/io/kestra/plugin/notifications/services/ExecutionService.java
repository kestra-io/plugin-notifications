package io.kestra.plugin.notifications.services;

import com.google.common.collect.Streams;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.RetryUtils;
import io.kestra.core.utils.UriProvider;
import io.kestra.plugin.notifications.ExecutionInterface;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class ExecutionService {
    public static Execution findExecution(RunContext runContext, String executionId) throws IllegalVariableEvaluationException, NoSuchElementException {
        ExecutionRepositoryInterface executionRepository = runContext.getApplicationContext().getBean(ExecutionRepositoryInterface.class);
        RetryUtils.Instance<Execution, NoSuchElementException> retryInstance = runContext.getApplicationContext().getBean(RetryUtils.class)
            .of(Exponential.builder()
                .delayFactor(2.0)
                .interval(Duration.ofSeconds(1))
                .maxInterval(Duration.ofSeconds(15))
                .maxAttempt(-1)
                .maxDuration(Duration.ofMinutes(10))
                .build(),
                runContext.logger()
            );

        String executionRendererId = runContext.render(runContext.render(executionId));

        return retryInstance.run(
            NoSuchElementException.class,
            () -> executionRepository.findById(executionRendererId)
                .filter(e -> e.getState().getCurrent().isTerninated())
                .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionId + "'"))
        );
    }
    
    @SuppressWarnings("UnstableApiUsage")
    public static Map<String, Object> executionMap(RunContext runContext, ExecutionInterface executionInterface) throws IllegalVariableEvaluationException {
        Execution execution = ExecutionService.findExecution(runContext, executionInterface.getExecutionId());
        UriProvider uriProvider = runContext.getApplicationContext().getBean(UriProvider.class);

        Map<String, Object> templateRenderMap = new HashMap<>();
        templateRenderMap.put("duration", execution.getState().humanDuration());
        templateRenderMap.put("startDate", execution.getState().getStartDate());
        templateRenderMap.put("link", uriProvider.executionUrl(execution));
        templateRenderMap.put("execution", JacksonMapper.toMap(execution));

        if (executionInterface.getCustomMessage() != null) {
            templateRenderMap.put("customMessage", runContext.render(executionInterface.getCustomMessage()));
        }

        if (executionInterface.getCustomFields() != null) {
            templateRenderMap.put("customFields", runContext.render(executionInterface.getCustomFields()));
        }

        Streams
            .findLast(execution.getTaskRunList()
                .stream()
                .filter(t -> t.getState().getCurrent() == State.Type.FAILED)
            )
            .ifPresentOrElse(
                taskRun -> templateRenderMap.put("firstFailed", taskRun),
                () -> templateRenderMap.put("firstFailed", false)
            );

        return templateRenderMap;
    } 
}
