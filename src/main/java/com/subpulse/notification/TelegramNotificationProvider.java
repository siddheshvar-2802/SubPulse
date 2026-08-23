package com.subpulse.notification;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 100% Free Telegram Instant Alert Bot Provider.
 * Sends renewal alerts directly to a user's Telegram chat via Telegram Bot API.
 * <p>
 * Zero cloud cost. Unlimited notifications.
 */
@Slf4j
@Component
public class TelegramNotificationProvider implements NotificationProvider {

    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";

    @Value("${app.telegram.bot-token:${TELEGRAM_BOT_TOKEN:}}")
    private String botToken;

    private final RestTemplate restTemplate;

    public TelegramNotificationProvider() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public void sendAlert(User user, Subscription subscription, int daysRemaining, String destination) {
        if (destination == null || destination.isBlank()) {
            log.warn("Telegram destination (chat_id) not set for subscription '{}'",
                    subscription.getServiceName());
            return;
        }

        String chatId = destination.trim();
        String text = buildMessage(user, subscription, daysRemaining);

        // If no Telegram bot token configured, simulate cleanly in logs
        if (botToken == null || botToken.isBlank() || "YOUR_BOT_TOKEN".equals(botToken)) {
            log.info("✈️ [TELEGRAM ALERT SIMULATION] -> To Chat ID: {} | Service: '{}' | Days: {}",
                    chatId, subscription.getServiceName(), daysRemaining);
            return;
        }

        String url = String.format(TELEGRAM_API, botToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("parse_mode", "Markdown");

        try {
            restTemplate.postForObject(url, payload, String.class);
            log.info("✈️ Telegram renewal alert successfully delivered to chat_id {} for '{}'",
                    chatId, subscription.getServiceName());
        } catch (Exception e) {
            log.error("Failed to send Telegram alert to chat_id {}: {}", chatId, e.getMessage());
            // Do not throw unhandled exception to prevent breaking batch scheduler jobs
        }
    }

    private String buildMessage(User user, Subscription subscription, int daysRemaining) {
        String urgencyHeader = daysRemaining == 0
                ? "🚨 *URGENT: RENEWS TODAY!*"
                : (daysRemaining <= 3 ? "⚠️ *UPCOMING RENEWAL ALERT*" : "🔔 *RENEWAL REMINDER*");

        String userName = (user != null && user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : "SubPulse User";

        String category = subscription.getCategory() != null ? subscription.getCategory().name() : "OTHER";
        String cycle = subscription.getBillingCycle() != null ? subscription.getBillingCycle().name().toLowerCase() : "monthly";

        return """
                %s
                
                Hi *%s*, your subscription renewal is coming up!
                
                📦 *Service:* `%s`
                📁 *Category:* `%s`
                💰 *Amount:* *%s %.2f* / %s
                📅 *Renewal Date:* *%s*
                ⏳ *Time Remaining:* *%d day(s)*
                
                ⚡ _Tracked via SubPulse Renewal Risk Engine_
                """.formatted(
                urgencyHeader,
                userName,
                subscription.getServiceName(),
                category,
                subscription.getCurrency() != null ? subscription.getCurrency() : "USD",
                subscription.getAmount() != null ? subscription.getAmount() : 0.0,
                cycle,
                subscription.getNextBillingDate() != null ? subscription.getNextBillingDate().toString() : "N/A",
                daysRemaining
        );
    }
}
