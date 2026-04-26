package com.app.shared.email;

import com.app.config.EmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public void send(String to, String subject, String body) {
        if (!emailProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(to)) {
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(emailProperties.getFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception ex) {
            // Ne doit pas casser le flux métier ; log et on continue.
            log.warn("Email send failed: {}", ex.getMessage());
        }
    }
}

