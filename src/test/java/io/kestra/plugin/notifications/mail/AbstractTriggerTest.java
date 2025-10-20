package io.kestra.plugin.notifications.mail;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.notifications.AbstractNotificationTest;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.worker.DefaultWorker;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@KestraTest
public abstract class AbstractTriggerTest extends AbstractNotificationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup[] {
                    new ServerSetup(3144, "127.0.0.1", ServerSetup.PROTOCOL_IMAP),
                    new ServerSetup(3145,"127.0.0.1",ServerSetup.PROTOCOL_POP3)
            }).withConfiguration(GreenMailConfiguration.aConfig().withUser("test@localhost", "password"))
            .withPerMethodLifecycle(false);

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    protected static class TestContext {
        final DefaultWorker worker;
        final AbstractScheduler scheduler;
        final Thread workerThread;
        final Thread schedulerThread;
        final Flux<Execution> receive;

        TestContext(ApplicationContext applicationContext, FlowListeners flowListeners, QueueInterface<Execution> queue,
                String flowId, CountDownLatch latch) {
            this.worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
            this.scheduler = new JdbcScheduler(applicationContext, flowListeners);
            this.workerThread = new Thread(worker::run);
            this.schedulerThread = new Thread(scheduler::run);
            this.receive = TestsUtils.receive(queue, execution -> {
                if (execution.getLeft().getFlowId().equals(flowId)) {
                    latch.countDown();
                }
            });
        }

        void start() {
            workerThread.start();
            schedulerThread.start();
        }

        void shutdown() {
            try {
                worker.shutdown();
                scheduler.close();
                receive.blockLast();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @BeforeAll
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    protected void sendTestEmail(String subject, String from, String body) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("test@localhost"));
        message.setSubject(subject);
        message.setText(body);
        greenMail.getUserManager().getUser("test@localhost").deliver(message);
    }
}