package io.kestra.plugin.notifications.mail;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AbstractMailTrigger extends AbstractTrigger {

    public Duration getInterval(){
        return Duration.ofSeconds(60);
    }
    @Schema(title = "Mail server protocol", description = "The protocol to use for connecting to the mail server")
    @Builder.Default
    protected final Property<MailService.Protocol> protocol = Property.ofValue(MailService.Protocol.IMAP);

    @Schema(title = "Mail server host", description = "The hostname or IP address of the mail server")
    @NotNull
    protected Property<String> host;

    @Schema(title = "Mail server port", description = "The port number of the mail server. Defaults: IMAP=993 (SSL), 143 (non-SSL); POP3=995 (SSL), 110 (non-SSL)")
    protected Property<Integer> port;

    @Schema(title = "Username", description = "The username for authentication")
    @NotNull
    protected Property<String> username;

    @Schema(title = "Password", description = "The password for authentication")
    @NotNull
    protected Property<String> password;

    @Schema(title = "Mail folder", description = "The mail folder to monitor (IMAP only)")
    @Builder.Default
    protected final Property<String> folder = Property.ofValue("INBOX");

    @Schema(title = "Use SSL", description = "Whether to use SSL/TLS encryption")
    @Builder.Default
    protected final Property<Boolean> ssl = Property.ofValue(true);

    @Schema(title = "Trust all certificates", description = "Whether to trust all SSL certificates (use with caution)")
    @Builder.Default
    protected final Property<Boolean> trustAllCertificates = Property.ofValue(false);

    @Schema(title = "Check interval", description = "How frequently to check for new emails")
    @Builder.Default
    protected final Property<Duration> interval = Property.ofValue(Duration.ofSeconds(60));

    protected MailService.MailConfiguration renderMailConfiguration(RunContext runContext) throws Exception {
        String rProtocol = String.valueOf(runContext.render(this.protocol).as(MailService.Protocol.class).orElseThrow());
        String rHost = runContext.render(this.host).as(String.class).orElseThrow();
        String rUsername = runContext.render(this.username).as(String.class).orElseThrow();
        String rPassword = runContext.render(this.password).as(String.class).orElseThrow();
        String rFolder = runContext.render(this.folder).as(String.class).orElse("INBOX");
        Boolean rSsl = runContext.render(this.ssl).as(Boolean.class).orElse(true);
        Boolean rTrustAllCertificates = runContext.render(this.trustAllCertificates).as(Boolean.class).orElse(false);
        Duration rInterval = runContext.render(this.interval).as(Duration.class).orElse(getInterval());

        Integer rPort = runContext.render(this.port).as(Integer.class)
            .orElse(MailService.getDefaultPort(MailService.Protocol.valueOf(rProtocol), rSsl));

        return new MailService.MailConfiguration(rProtocol, rHost, rPort, rUsername, rPassword, rFolder, rSsl, rTrustAllCertificates, rInterval);
    }
}