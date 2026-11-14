package io.noshow.notify;

import io.noshow.domain.AlertChannel;
import io.noshow.domain.ChannelType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends to an EMAIL channel, whose {@code config} is the recipient address.
 * Created only when SMTP is configured ({@code spring.mail.host}).
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailChannelSender implements ChannelSender {

    private final JavaMailSender mail;
    private final String from;

    public EmailChannelSender(JavaMailSender mail,
                              @Value("${noshow.alerts.from:noreply@noshow.local}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(AlertChannel channel, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(channel.getConfig());
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
