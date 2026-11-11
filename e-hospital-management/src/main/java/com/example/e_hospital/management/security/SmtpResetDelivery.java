package com.example.e_hospital.management.security;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpResetDelivery implements ResetDelivery {
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean enabled;
    private final String baseUrl;
    private final String from;
    public SmtpResetDelivery(ObjectProvider<JavaMailSender> mail, Environment environment,
            @Value("${hospital.recovery.enabled:false}") boolean enabled,
            @Value("${hospital.recovery.base-url:}") String baseUrl,
            @Value("${hospital.recovery.from:}") String from) {
        this.mail = mail; this.enabled = enabled; this.baseUrl = baseUrl.replaceAll("/+$", ""); this.from = from;
        if (enabled) {
            URI uri;
            try { uri = URI.create(baseUrl); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("Configure a valid trusted recovery URL."); }
            boolean localDev = environment.acceptsProfiles(Profiles.of("dev", "test"))
                && "http".equals(uri.getScheme()) && uri.getHost() != null
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (!("https".equals(uri.getScheme()) || localDev) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !from.matches("[^\\s@]+@[^\\s@]+") || mail.getIfAvailable() == null) {
                throw new IllegalStateException("Configure a trusted recovery URL, sender and SMTP server before enabling recovery.");
            }
            boolean disposable = environment.acceptsProfiles(Profiles.of("dev", "test"));
            boolean encrypted = environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", Boolean.class, false)
                || environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false);
            if ((!disposable && !encrypted) || environment.getProperty("spring.mail.properties.mail.debug", Boolean.class, false)) {
                throw new IllegalStateException("Recovery SMTP requires encryption outside development and must not enable mail debugging.");
            }
        }
    }
    @Override public boolean available() { return enabled; }
    @Override public void send(String email, String token) {
        if (!enabled) throw new IllegalStateException("Recovery delivery is disabled");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("Reset your CareBridge password");
        // Fragment avoids putting the bearer token in HTTP request URLs and referrer headers.
        message.setText("A password reset was requested for your hospital account. This single-use link expires in 30 minutes:\n\n"
            + baseUrl + "/reset-password#token=" + token + "\n\nIf you did not request this, ignore this message.");
        mail.getObject().send(message);
    }
}
