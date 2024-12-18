package io.kestra.plugin.notifications.services;

import com.google.common.collect.Streams;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
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
    public static Execution findExecution(RunContext runContext, Property<String> executionId) throws IllegalVariableEvaluationException, NoSuchElementException {
        ExecutionRepositoryInterface executionRepository = ((DefaultRunContext)runContext).getApplicationContext().getBean(ExecutionRepositoryInterface.class);
        RetryUtils.Instance<Execution, NoSuchElementException> retryInstance = ((DefaultRunContext)runContext).getApplicationContext().getBean(RetryUtils.class)
            .of(Exponential.builder()
                .delayFactor(2.0)
                .interval(Duration.ofSeconds(1))
                .maxInterval(Duration.ofSeconds(15))
                .maxAttempt(-1)
                .maxDuration(Duration.ofMinutes(10))
                .build(),
                runContext.logger()
            );

        String executionRendererId = runContext.render(runContext.render(executionId).as(String.class).orElse(null));

        var flowVars = (Map<String, String>) runContext.getVariables().get("flow");
        var executionVars = (Map<String, String>) runContext.getVariables().get("execution");
        var isCurrentExecution = executionRendererId.equals(executionVars.get("id"));
        if (isCurrentExecution) {
            runContext.logger().info("Loading execution data for the current execution.");
        }

        return retryInstance.run(
            NoSuchElementException.class,
            () -> executionRepository.findById(flowVars.get("tenantId"), executionRendererId)
                // we don't wait for current execution to be terminated as it could not be possible as long as this task is running
                // note that this check may exist due to previous usage with listeners, we may revisit this later
                .filter(e -> isCurrentExecution || e.getState().getCurrent().isTerminated())
                .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionRendererId + "'"))
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    public static Map<String, Object> executionMap(RunContext runContext, ExecutionInterface executionInterface) throws IllegalVariableEvaluationException {
        Execution execution = ExecutionService.findExecution(runContext, executionInterface.getExecutionId());
        UriProvider uriProvider = ((DefaultRunContext)runContext).getApplicationContext().getBean(UriProvider.class);

        Map<String, Object> templateRenderMap = new HashMap<>();
        templateRenderMap.put("duration", execution.getState().humanDuration());
        templateRenderMap.put("startDate", execution.getState().getStartDate());
        templateRenderMap.put("link", uriProvider.executionUrl(execution));
        templateRenderMap.put("execution", JacksonMapper.toMap(execution));

        runContext.render(executionInterface.getCustomMessage())
            .as(String.class)
            .ifPresent(s -> templateRenderMap.put("customMessage", s));

        final Map<String, Object> renderedCustomFields = runContext.render(executionInterface.getCustomFields()).asMap(String.class, Object.class);
        if (!renderedCustomFields.isEmpty()) {
            templateRenderMap.put("customFields", renderedCustomFields);
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
