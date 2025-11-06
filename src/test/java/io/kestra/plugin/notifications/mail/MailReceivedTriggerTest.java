package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class MailReceivedTriggerTest extends AbstractTriggerTest {

    @Test
    void MailReceivedTriggerWithPop3() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        MailReceivedTrigger mailTrigger = MailReceivedTrigger.builder()
                .id("pop3-mail-trigger")
                .type(MailReceivedTrigger.class.getName())
                .protocol(Property.ofValue(MailService.Protocol.POP3))
                .host(Property.ofValue("127.0.0.1"))
                .port(Property.ofValue(3145))
                .username(Property.ofValue("test@localhost"))
                .password(Property.ofValue("password"))
                .ssl(Property.ofValue(false))
                .trustAllCertificates(Property.ofValue(true))
                .interval(Property.ofValue(Duration.ofSeconds(1)))
                .build();

        Flow testFlow = Flow.builder()
                .id("mail-received-trigger-pop3")
                .namespace("io.kestra.tests")
                .revision(1)
                .tasks(Collections.singletonList(Return.builder()
                        .id("process-pop3-emails")
                        .type(Return.class.getName())
                        .format(Property.ofValue(
                                "POP3 emails received: {{trigger.total}} emails, latest: {{trigger.latestEmail.subject}}"))
                        .build()))
                .triggers(Collections.singletonList(mailTrigger))
                .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("mail-received-trigger-pop3")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        TestContext testContext = new TestContext(applicationContext, flowListenersServiceSpy, executionQueue,
                "mail-received-trigger-pop3", queueCount);

        try {
            testContext.start();

            Thread.sleep(Duration.ofSeconds(1).toMillis());

            sendTestEmail("First Email", "sender1@example.com", "First test email body");

            boolean await = queueCount.await(25, TimeUnit.SECONDS);
            assertThat("POP3 emails trigger should execute", await, is(true));

            try {
                Await.until(
                    () -> lastExecution.get() != null,
                    Duration.ofMillis(100),
                    Duration.ofSeconds(2)
                );
            } catch (TimeoutException e) {
                throw new AssertionError("Execution was not captured within 2 seconds", e);
            }

            Execution execution = lastExecution.get();

            Map<String, Object> triggerVars = execution.getTrigger().getVariables();
            assertThat("Should have received emails", (Integer) triggerVars.get("total"), greaterThan(0));

            @SuppressWarnings("unchecked")
            Map<String, Object> latestEmail = (Map<String, Object>) triggerVars.get("latestEmail");
            assertThat("Latest email should have a subject", latestEmail.get("subject"), notNullValue());
            assertThat("Latest email should have a from address", latestEmail.get("from"), notNullValue());
            assertThat("Latest email subject should be one of the sent emails",
                    latestEmail.get("subject"),
                    (is("First Email")));
        } finally {
            testContext.shutdown();
            receive.blockLast();
        }
    }

    @Test
    void MailReceivedTriggerWithImap() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        MailReceivedTrigger mailTrigger = MailReceivedTrigger.builder()
                .id("imap-mail-trigger")
                .type(MailReceivedTrigger.class.getName())
                .protocol(Property.ofValue(MailService.Protocol.IMAP))
                .host(Property.ofValue("127.0.0.1"))
                .port(Property.ofValue(3144))
                .username(Property.ofValue("test@localhost"))
                .password(Property.ofValue("password"))
                .ssl(Property.ofValue(false))
                .trustAllCertificates(Property.ofValue(true))
                .interval(Property.ofValue(Duration.ofSeconds(1)))
                .build();

        Flow testFlow = Flow.builder()
                .id("mail-received-trigger-imap")
                .namespace("io.kestra.tests")
                .revision(1)
                .tasks(Collections.singletonList(Return.builder()
                        .id("process-imap-emails")
                        .type(Return.class.getName())
                        .format(Property.ofValue(
                                "IMAP emails received: {{trigger.total}} emails, latest: {{trigger.latestEmail.subject}}"))
                        .build()))
                .triggers(Collections.singletonList(mailTrigger))
                .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("mail-received-trigger-imap")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        TestContext testContext = new TestContext(applicationContext, flowListenersServiceSpy, executionQueue,
                "mail-received-trigger-imap", queueCount);

        try {
            testContext.start();

            Thread.sleep(Duration.ofSeconds(1).toMillis());

            sendTestEmail("First Email", "sender1@example.com", "First test email body");

            boolean await = queueCount.await(25, TimeUnit.SECONDS);
            assertThat("IMAP emails trigger should execute", await, is(true));

            try {
                Await.until(
                    () -> lastExecution.get() != null,
                    Duration.ofMillis(100),
                    Duration.ofSeconds(2)
                );
            } catch (TimeoutException e) {
                throw new AssertionError("Execution was not captured within 2 seconds", e);
            }

            Execution execution = lastExecution.get();

            Map<String, Object> triggerVars = execution.getTrigger().getVariables();
            assertThat("Should have received emails", (Integer) triggerVars.get("total"), greaterThan(0));

            @SuppressWarnings("unchecked")
            Map<String, Object> latestEmail = (Map<String, Object>) triggerVars.get("latestEmail");
            assertThat("Latest email should have a subject", latestEmail.get("subject"), notNullValue());
            assertThat("Latest email should have a from address", latestEmail.get("from"), notNullValue());
            assertThat("Latest email subject should be one of the sent emails",
                latestEmail.get("subject"),
                (is("First Email")));
        } finally {
            testContext.shutdown();
            receive.blockLast();
        }
    }
}
