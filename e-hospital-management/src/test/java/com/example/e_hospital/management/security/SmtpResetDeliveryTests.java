package com.example.e_hospital.management.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpResetDeliveryTests {
    @SuppressWarnings("unchecked")
    @Test void smtpMessagesUseTrustedFragmentLinksAndNeverRequestHostHeaders() {
        var mail = mock(JavaMailSender.class); ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail); when(provider.getObject()).thenReturn(mail);
        var environment = new MockEnvironment().withProperty("spring.mail.properties.mail.smtp.starttls.required", "true");
        var delivery = new SmtpResetDelivery(provider, environment, true, "https://hospital.example.invalid", "care@example.invalid");
        String token = TokenSecrets.generate(); delivery.send("staff@example.invalid", token);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(message.capture());
        assertTrue(message.getValue().getText().contains("https://hospital.example.invalid/reset-password#token=" + token));
        assertFalse(message.getValue().getText().contains("?token="));
        assertThrows(IllegalStateException.class, () -> new SmtpResetDelivery(provider, environment, true,
            "http://hospital.example.invalid", "care@example.invalid"));
        environment.withProperty("spring.mail.properties.mail.debug", "true");
        assertThrows(IllegalStateException.class, () -> new SmtpResetDelivery(provider, environment, true,
            "https://hospital.example.invalid", "care@example.invalid"));
    }
}
