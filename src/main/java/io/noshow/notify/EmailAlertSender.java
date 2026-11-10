package io.noshow.notify;

import io.noshow.domain.NotificationOutbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends notifications as plain email. Only created when SMTP is configured
 * ({@code spring.mail.host} set), so a deployment without mail configured
 * simply has no sender and queues nothing to deliver.
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailAlertSender implements AlertSender {

    private final JavaMailSender mail;
    private final String from;

    public EmailAlertSender(JavaMailSender mail,
                            @Value("${noshow.alerts.from:noreply@noshow.local}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(NotificationOutbox notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipient());
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());
        mail.send(message);
    }
}
