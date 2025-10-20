package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notifications.mail.MailService.EmailData;
import io.kestra.plugin.notifications.mail.MailService.MailConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import jakarta.mail.internet.MimeMessage;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when an email is received in real-time.",
    description = """
        Monitor a mailbox for new emails via IMAP or POP3 protocols and create one execution per email received.
        For IMAP, uses the IDLE command for true real-time monitoring.
        For POP3, uses polling.
        If you would like to process multiple emails in batch, use the MailReceivedTrigger instead.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Monitor Gmail inbox for new emails in real-time",
            full = true,
            code = """
                id: realtime_email_monitor
                namespace: company.team

                tasks:
                  - id: process_email
                    type: io.kestra.core.tasks.log.Log
                    message: |
                      Real-time email received:
                      Subject: {{ trigger.subject }}
                      From: {{ trigger.from }}
                      Date: {{ trigger.date }}
                      Body: {{ trigger.body }}

                triggers:
                  - id: realtime_gmail_trigger
                    type: io.kestra.plugin.notifications.mail.RealTimeTrigger
                    protocol: IMAP
                    host: imap.gmail.com
                    port: 993
                    username: "{{ secret('GMAIL_USERNAME') }}"
                    password: "{{ secret('GMAIL_PASSWORD') }}"
                    folder: INBOX
                    ssl: true
                """
        ),
        @Example(
            title = "Monitor POP3 mailbox in real-time",
            code = """
                triggers:
                  - id: realtime_pop3_trigger
                    type: io.kestra.plugin.notifications.mail.RealTimeTrigger
                    protocol: POP3
                    host: pop.example.com
                    port: 995
                    username: "{{ secret('EMAIL_USERNAME') }}"
                    password: "{{ secret('EMAIL_PASSWORD') }}"
                    ssl: true
                    interval: PT30S
                """
        )
    }
)
public class RealTimeTrigger extends AbstractMailTrigger
    implements RealtimeTriggerInterface, TriggerOutput<MailService.EmailData> {

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean isActive = new AtomicBoolean(true);

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final CountDownLatch waitForTermination = new CountDownLatch(1);

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicReference<Store> activeStore = new AtomicReference<>();

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicReference<Folder> activeFolder = new AtomicReference<>();

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicReference<ZonedDateTime> lastFetched = new AtomicReference<>();

    @Override
    public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        MailService.MailConfiguration mailConfig = renderMailConfiguration(runContext);

        runContext.logger().info("Starting real-time email monitoring using {} protocol on {}:{}",
            mailConfig.protocol, mailConfig.host, mailConfig.port);

        return createRealtimeEmailStream(runContext, mailConfig)
            .map(emailData -> {
                runContext.logger().info("Real-time trigger: New email from '{}' with subject '{}'",
                    emailData.getFrom(), emailData.getSubject());
                return TriggerService.generateRealtimeExecution(this, conditionContext, context, emailData);
            })
            .onErrorContinue(
                (throwable, o) -> runContext.logger().error("Error in real-time email stream", throwable))
            .doFinally(signalType -> {
                runContext.logger().info("Email stream finished with signal: {}", signalType);
                this.waitForTermination.countDown();
            });
    }

    private Flux<EmailData> createRealtimeEmailStream(RunContext runContext, MailConfiguration config) {
        if ("IMAP".equals(config.protocol)) {
            return createImapIdleStream(runContext, config);
        }

        return createPop3PollingStream(runContext, config);
    }

    private Flux<EmailData> createImapIdleStream(RunContext runContext, MailConfiguration config) {
        return Flux.create(sink -> {
            Store store = null;
            Folder folder = null;

            try {
                Properties props = MailService.setupMailProperties(config.protocol, config.host, config.port,
                    config.ssl, config.trustAllCertificates, runContext);
                Session session = Session.getInstance(props, null);
                store = session.getStore(MailService.getProtocolName(config.protocol, config.ssl));

                MailService.connectToStore(store, config.host, config.port, config.username, config.password,
                    runContext);
                folder = store.getFolder(config.folder);
                folder.open(Folder.READ_ONLY);

                // Store references for cleanup
                activeStore.set(store);
                activeFolder.set(folder);

                runContext.logger().info("Connected to {}:{}", config.host, config.port);
                runContext.logger().info("Starting IMAP IDLE monitoring on folder: {}", config.folder);

                final Folder finalFolder = folder;
                folder.addMessageCountListener(new MessageCountListener() {
                    @Override
                    public void messagesAdded(MessageCountEvent e) {
                        try {
                            for (Message message : e.getMessages()) {
                                if (!isActive.get())
                                    break;

                                if (message instanceof MimeMessage mimeMessage) {
                                    EmailData emailData = MailService.parseEmailData(mimeMessage);
                                    if (emailData != null) {
                                        runContext.logger().info("IMAP IDLE: New email - Subject: '{}', From: '{}'",
                                            emailData.getSubject(), emailData.getFrom());
                                        sink.next(emailData);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            if (isActive.get()) {
                                runContext.logger().error("Error processing new messages", ex);
                            }
                        }
                    }

                    @Override
                    public void messagesRemoved(MessageCountEvent e) {
                    }
                });

                // Start IDLE using reflection to support different IMAP implementations
                while (isActive.get() && finalFolder.isOpen()) {
                    try {
                        idleFolder(finalFolder, runContext);
                    } catch (Exception e) {
                        if (isActive.get()) {
                            runContext.logger().error("IMAP IDLE error", e);
                            sink.error(e);
                            break;
                        }
                    }

                    // Check if thread was interrupted
                    if (Thread.currentThread().isInterrupted()) {
                        isActive.set(false);
                        break;
                    }
                }

            } catch (Exception e) {
                if (isActive.get()) {
                    sink.error(e);
                }
            } finally {
                sink.complete();
                cleanupImapResources(runContext, store, folder);
            }

        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void idleFolder(Folder folder, RunContext runContext) throws Exception {
        // Use reflection to call idle() method to support both com.sun.mail.imap.IMAPFolder
        // and org.eclipse.angus.mail.imap.IMAPFolder implementations
        try {
            Method idleMethod = folder.getClass().getMethod("idle");
            idleMethod.invoke(folder);
        } catch (NoSuchMethodException e) {
            runContext.logger().warn("IDLE method not available for folder type: {}, falling back to polling",
                folder.getClass().getName());
            Thread.sleep(1000);
        } catch (Exception e) {
            throw new MessagingException("Failed to invoke IDLE on folder", e);
        }
    }

    private void cleanupImapResources(RunContext runContext, Store store, Folder folder) {
        try {
            runContext.logger().info("Cleaning up IMAP resources");

            if (folder != null && folder.isOpen()) {
                try {
                    folder.close(false);
                    runContext.logger().debug("IMAP folder closed");
                } catch (Exception e) {
                    runContext.logger().warn("Error closing IMAP folder", e);
                }
            }

            if (store != null && store.isConnected()) {
                try {
                    store.close();
                    runContext.logger().debug("IMAP store closed");
                } catch (Exception e) {
                    runContext.logger().warn("Error closing IMAP store", e);
                }
            }
        } catch (Exception e) {
            runContext.logger().warn("Error during IMAP cleanup", e);
        }
    }

    private Flux<EmailData> createPop3PollingStream(RunContext runContext, MailConfiguration config) {

        if (lastFetched.get() == null) {
            lastFetched.set(ZonedDateTime.now().minus(config.interval));
            runContext.logger().info("Initialized POP3 polling with lastFetched: {}", lastFetched.get());
        }

        return Flux.interval(Duration.ZERO, config.interval)
            .takeWhile(tick -> isActive.get())
            .doOnNext(tick -> runContext.logger().info("POP3 polling cycle: {}", tick))
            .flatMap(tick -> {
                try {
                    if (!isActive.get()) {
                        return Flux.empty();
                    }

                    ZonedDateTime currentLastFetched = lastFetched.get();
                    runContext.logger().info("POP3 polling: checking for emails after {}", currentLastFetched);

                    List<MailService.EmailData> newEmails = MailService.fetchNewEmails(runContext, config.protocol,
                        config.host, config.port, config.username, config.password, config.folder,
                        config.ssl, config.trustAllCertificates, currentLastFetched);

                    if (!newEmails.isEmpty()) {
                        ZonedDateTime latestEmailDate = newEmails.stream()
                            .map(MailService.EmailData::getDate)
                            .filter(Objects::nonNull)
                            .max(ZonedDateTime::compareTo)
                            .orElse(currentLastFetched);

                        lastFetched.set(latestEmailDate);

                        runContext.logger().info("POP3 polling: found {} new emails, updated lastFetched to {}",
                            newEmails.size(), latestEmailDate);
                    } else {
                        runContext.logger().info("POP3 polling: no new emails found");
                    }

                    return Flux.fromIterable(newEmails);
                } catch (Exception e) {
                    if (isActive.get()) {
                        runContext.logger().error("Error in POP3 polling", e);
                    }
                    return Flux.empty();
                }
            }, 1);
    }

    @Override
    public void kill() {
        stop(true);
    }

    @Override
    public void stop() {
        stop(false); // must be non-blocking
    }

    private void stop(boolean wait) {
        if (!isActive.compareAndSet(true, false)) {
            return;
        }

        Folder folder = activeFolder.get();
        if (folder != null) {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (Exception ignored) {
            }
        }

        Store store = activeStore.get();
        if (store != null) {
            try {
                if (store.isConnected()) {
                    store.close();
                }
            } catch (Exception ignored) {
            }
        }

        if (wait) {
            try {
                this.waitForTermination.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}