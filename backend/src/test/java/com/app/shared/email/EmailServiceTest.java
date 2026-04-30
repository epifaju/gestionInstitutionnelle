package com.app.shared.email;

import com.app.config.EmailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailProperties emailProperties;

    @Test
    void send_quandDesactive_neFaitRien() {
        when(emailProperties.isEnabled()).thenReturn(false);
        EmailService svc = new EmailService(mailSender, emailProperties);
        svc.send("a@test.com", "sub", "body");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_sansDestinataire_neFaitRien() {
        when(emailProperties.isEnabled()).thenReturn(true);
        EmailService svc = new EmailService(mailSender, emailProperties);
        svc.send("   ", "sub", "body");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_ok_appelleMailSender() {
        when(emailProperties.isEnabled()).thenReturn(true);
        when(emailProperties.getFrom()).thenReturn("no-reply@test.com");
        EmailService svc = new EmailService(mailSender, emailProperties);
        svc.send("a@test.com", "sub", "body");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}

