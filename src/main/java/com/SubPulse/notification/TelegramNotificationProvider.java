package com.subpulse.notification;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Telegram notification provider.
 * Sends renewal alerts to a Telegram chat via the Telegram Bot API.
 *
 * Setup:
 *  1. Create a bot with @BotFather → get bot token.
 *  2. Start a chat with your bot → get chat_id.
 *  3. Set destination = chat_id in AlertConfig.
 *  4. Add TELEGRAM_BOT_TOKEN to application.yml.
 */
@Slf4j
@Component
public class TelegramNotificationProvider implements NotificationProvider {

    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";

    // Inject from application.yml (add: app.telegram.bot-token)
    private final String botToken;
    private final RestTemplate restTemplate;

    public TelegramNotificationProvider() {
        // Reads from environment variable TELEGRAM_BOT_TOKEN if set, else stub
        this.botToken    = System.getenv().getOrDefault("TELEGRAM_BOT_TOKEN", "YOUR_BOT_TOKEN");
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

        String text = buildMessage(user, subscription, daysRemaining);
        String url  = String.format(TELEGRAM_API, botToken);

        Map<String, Object> payload = Map.of(
                "chat_id",    destination,
                "text",       text,
                "parse_mode", "Markdown"
        );

        try {
            restTemplate.postForObject(url, payload, String.class);
            log.info("Telegram alert sent to chat_id {} for subscription '{}'",
                    destination, subscription.getServiceName());
        } catch (Exception e) {
            log.error("Failed to send Telegram alert: {}", e.getMessage());
            throw new RuntimeException("Telegram delivery failed: " + e.getMessage(), e);
        }
    }

    private String buildMessage(User user, Subscription subscription, int daysRemaining) {
        String urgency = daysRemaining == 0 ? "🚨 *RENEWS TODAY*" : "🔔 *Renewal Reminder*";
        return """
                %s
                
                Hi *%s*!
                
                📦 *%s* is renewing in *%d day(s)*.
                💰 Amount : *%s %s*
                📅 Date   : *%s*
                """.formatted(
                urgency,
                user.getFullName(),
                subscription.getServiceName(),
                daysRemaining,
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getNextBillingDate()
        );
    }
}
