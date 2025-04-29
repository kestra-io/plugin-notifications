package io.kestra.plugin.notifications.service;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.kestra.plugin.notifications.services.ExecutionService.isExecutionInTheWantedState;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionServiceTest {
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
}
