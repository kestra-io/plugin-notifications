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

class RealTimeTriggerTest extends AbstractTriggerTest {

    @Test
    void RealTimeTriggerWithPop3() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        RealTimeTrigger mailTrigger = RealTimeTrigger.builder()
                .id("pop3-mail-trigger")
                .type(RealTimeTrigger.class.getName())
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
                .id("real-time-trigger-pop3")
                .namespace("io.kestra.tests")
                .revision(1)
                .tasks(Collections.singletonList(Return.builder()
                        .id("process-pop3-emails")
                        .type(Return.class.getName())
                        .format(Property.ofValue(
                                "POP3 email received: {{trigger.subject}} from {{trigger.from}}"))
                        .build()))
                .triggers(Collections.singletonList(mailTrigger))
                .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("real-time-trigger-pop3")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        TestContext testContext = new TestContext(applicationContext, flowListenersServiceSpy, executionQueue,
                "real-time-trigger-pop3", queueCount);

        try {
            testContext.start();
            sendTestEmail("Test Email", "sender@example.com", "Test email body");

            Thread.sleep(Duration.ofSeconds(2).toMillis());

            boolean await = queueCount.await(20, TimeUnit.SECONDS);
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
            assertThat("Latest email subject should be present", triggerVars.get("subject"), notNullValue());
            assertThat("Latest email sender should be present", triggerVars.get("from"), notNullValue());
            assertThat("Latest email body should be present", triggerVars.get("body"), notNullValue());
        } finally {
            testContext.shutdown();
            receive.blockLast();
        }
    }

    @Test
    void RealTimeTriggerWithImap() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        RealTimeTrigger mailTrigger = RealTimeTrigger.builder()
                .id("imap-real-time-trigger")
                .type(RealTimeTrigger.class.getName())
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
                .id("real-time-trigger-imap")
                .namespace("io.kestra.tests")
                .revision(1)
                .tasks(Collections.singletonList(Return.builder()
                        .id("process-imap-emails")
                        .type(Return.class.getName())
                        .format(Property.ofValue(
                                "IMAP email received: {{trigger.subject}} from {{trigger.from}}"))
                        .build()))
                .triggers(Collections.singletonList(mailTrigger))
                .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("real-time-trigger-imap")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        TestContext testContext = new TestContext(applicationContext, flowListenersServiceSpy, executionQueue,
                "real-time-trigger-imap", queueCount);

        try {
            testContext.start();

            Thread.sleep(Duration.ofSeconds(2).toMillis());

            sendTestEmail("Test Email", "sender@example.com", "Test email body");
            boolean await = queueCount.await(30, TimeUnit.SECONDS);
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
            assertThat("Latest email subject should be present", triggerVars.get("subject"), notNullValue());
            assertThat("Latest email sender should be present", triggerVars.get("from"), notNullValue());
            assertThat("Latest email body should be present", triggerVars.get("body"), notNullValue());
        } finally {
            testContext.shutdown();
            receive.blockLast();
        }
    }
}