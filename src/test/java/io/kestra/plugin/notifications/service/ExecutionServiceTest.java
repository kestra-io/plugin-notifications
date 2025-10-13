package io.kestra.plugin.notifications.service;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.notifications.ExecutionInterface;
import io.kestra.plugin.notifications.services.ExecutionService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.plugin.notifications.services.ExecutionService.isExecutionInTheWantedState;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionServiceTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    // same execution
    @Test
    void isExecutionInTheWantedState_sameExecution() {
        assertThat(
            isExecutionInTheWantedState(
                Execution.builder().state(new State(State.Type.RUNNING)).build(),
                true,
                Optional.empty())
        ).isTrue();
    }

    // coming from a Flow trigger
    @Test
    void isExecutionInTheWantedState_flowTrigger_reactingOn_failed() {
        assertThat(
            isExecutionInTheWantedState(
                Execution.builder().state(new State(State.Type.FAILED)).build(),
                false,
                Optional.of(State.Type.FAILED.name()))
        ).isTrue();
    }

    // Elastic stack data un-synchronized because of indexer
    @Test
    void isExecutionInTheWantedState_flowTrigger_reactingOn_running() {
        assertThat(
            isExecutionInTheWantedState(
                Execution.builder().state(new State(State.Type.RUNNING)).build(),
                false,
                Optional.of(State.Type.RUNNING.name()))
        ).isTrue();
    }

    @Test
    void isExecutionInTheWantedState_flowTrigger_reactingOn_running_thatFailedQuickly() {
        assertThat(
            isExecutionInTheWantedState(
                Execution.builder().state(new State(State.Type.FAILED)).build(),
                false,
                Optional.of(State.Type.RUNNING.name()))
        ).isTrue();
    }

    @Test
    void isExecutionInTheWantedState_flowTrigger_reactingOn_paused_notUpToDate() {
        assertThat(
            isExecutionInTheWantedState(
                Execution.builder().state(new State(State.Type.CREATED)).build(),
                false,
                Optional.of(State.Type.PAUSED.name()))
        ).isFalse();
    }

    @Test
    void executionMapShouldNotFailWhenNoTaskrun() throws IllegalVariableEvaluationException {
        var flow = Flow.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("namespace")
            .id("flow")
            .tasks(List.of(Log.builder().id("log").message("log message").build()))
            .build();
        var execution = Execution.newExecution(flow, Collections.emptyList())
            .withState(State.Type.FAILED);
        executionRepository.save(execution);
        var runContext = runContextFactory.of(flow, execution);

        var executionMap = ExecutionService.executionMap(runContext, new ExecutionInterface() {
            @Override
            public Property<String> getExecutionId() {
                return Property.ofValue(execution.getId());
            }

            @Override
            public Property<Map<String, Object>> getCustomFields() {
                return null;
            }

            @Override
            public Property<String> getCustomMessage() {
                return null;
            }
        });

        assertThat(executionMap).isNotNull();
        assertThat(executionMap).hasSize(4);
        assertThat(executionMap).containsKey("duration");
        assertThat(executionMap).containsKey("execution");
        assertThat(executionMap).containsKey("link");
        assertThat(executionMap).containsKey("startDate");
    }
}
