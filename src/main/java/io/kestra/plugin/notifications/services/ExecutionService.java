package io.kestra.plugin.notifications.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.RetryUtils;

import java.time.Duration;
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
}
