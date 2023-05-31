package io.kestra.plugin.notifications.teams;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.notifications.InvalidFlowTestHelper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolationException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TeamsInvalidTest extends InvalidFlowTestHelper {

    @Inject
    ModelValidator modelValidator;

    @Test
    void executionMissingUrl() {
        Flow flow = this.parse("flows/invalid/teams-execution-missing-url.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(2));

        assertThat(validate.get().getMessage(), containsString("url: must not be null"));
        assertThat(validate.get().getMessage(), containsString("url: must not be empty"));
    }

    @Test
    void incomingWebhookMissingUrl() {
        Flow flow = this.parse("flows/invalid/teams-incoming-webhook-missing-url.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(2));

        assertThat(validate.get().getMessage(), containsString("url: must not be null"));
        assertThat(validate.get().getMessage(), containsString("url: must not be empty"));
    }
}
