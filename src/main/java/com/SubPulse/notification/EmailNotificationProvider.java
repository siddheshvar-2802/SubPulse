package com.subpulse.notification;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email notification provider using Spring's JavaMailSender (SMTP).
 * Sends a plain-text renewal alert to the user's registered email address.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationProvider implements NotificationProvider {

    private final JavaMailSender mailSender;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void sendAlert(User user, Subscription subscription, int daysRemaining, String destination) {
        String subject = buildSubject(subscription, daysRemaining);
        String body    = buildBody(user, subscription, daysRemaining);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Email alert sent to {} for subscription '{}' ({} days remaining)",
                user.getEmail(), subscription.getServiceName(), daysRemaining);
    }

    private String buildSubject(Subscription subscription, int daysRemaining) {
        if (daysRemaining == 0) {
            return "⚠️ SubPulse: " + subscription.getServiceName() + " renews TODAY!";
        }
        return "🔔 SubPulse: " + subscription.getServiceName() + " renews in " + daysRemaining + " day(s)";
    }

    private String buildBody(User user, Subscription subscription, int daysRemaining) {
        return """
                Hi %s,
                
                This is a reminder that your subscription is coming up for renewal.
                
                📦 Service      : %s
                💰 Amount       : %s %s
                📅 Renews on    : %s
                ⏳ Days remaining: %d day(s)
                
                %s
                
                Stay on top of your subscriptions with SubPulse!
                
                — The SubPulse Team
                """.formatted(
                user.getFullName(),
                subscription.getServiceName(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getNextBillingDate(),
                daysRemaining,
                subscription.getWebsiteUrl() != null
                        ? "🌐 Manage at: " + subscription.getWebsiteUrl()
                        : ""
        );
    }
}
