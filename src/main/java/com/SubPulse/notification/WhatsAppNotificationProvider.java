package com.subpulse.notification;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * WhatsApp Notification Provider.
 * Sends renewal alerts to the user's WhatsApp number.
 *
 * Supports:
 *  1. Twilio WhatsApp API (configured via TWILIO_ACCOUNT_SID & TWILIO_AUTH_TOKEN)
 *  2. CallMeBot WhatsApp Gateway (configured via CALLMEBOT_API_KEY)
 *  3. Sandbox log simulation (when credentials are not yet configured)
 */
@Slf4j
@Component
public class WhatsAppNotificationProvider implements NotificationProvider {

    @Value("${app.whatsapp.twilio.account-sid:${TWILIO_ACCOUNT_SID:}}")
    private String twilioAccountSid;

    @Value("${app.whatsapp.twilio.auth-token:${TWILIO_AUTH_TOKEN:}}")
    private String twilioAuthToken;

    @Value("${app.whatsapp.twilio.from-number:${TWILIO_WHATSAPP_FROM:+14155238886}}")
    private String twilioFromNumber;

    @Value("${app.whatsapp.twilio.messaging-service-sid:${TWILIO_MESSAGING_SERVICE_SID:}}")
    private String messagingServiceSid;

    @Value("${app.whatsapp.twilio.content-sid:${TWILIO_CONTENT_SID:}}")
    private String twilioContentSid;

    @Value("${app.whatsapp.callmebot.api-key:${CALLMEBOT_API_KEY:}}")
    private String callmebotApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public void sendAlert(User user, Subscription subscription, int daysRemaining, String destination) {
        if (destination == null || destination.isBlank()) {
            log.warn("WhatsApp destination phone number not provided for subscription '{}'",
                    subscription.getServiceName());
            return;
        }

        // Clean phone number (ensure country code prefix e.g. +91 or +1)
        String phone = formatPhoneNumber(destination);
        String messageText = buildWhatsAppMessage(user, subscription, daysRemaining);

        // Option 1: Twilio WhatsApp API
        if (twilioAccountSid != null && !twilioAccountSid.isBlank() && !twilioAccountSid.startsWith("placeholder")) {
            sendViaTwilio(phone, messageText, subscription.getServiceName(), daysRemaining);
            return;
        }

        // Option 2: CallMeBot API
        if (callmebotApiKey != null && !callmebotApiKey.isBlank() && !callmebotApiKey.startsWith("placeholder")) {
            sendViaCallMeBot(phone, messageText, subscription.getServiceName());
            return;
        }

        // Option 3: Local Dev Sandbox Simulation Log
        log.info("""
                
                ══════════════════════════════════════════════════════════════════════
                💬 [WHATSAPP ALERT SIMULATION] -> To: {}
                ──────────────────────────────────────────────────────────────────────
                {}
                ══════════════════════════════════════════════════════════════════════
                """, phone, messageText);
    }

    private void sendViaTwilio(String toPhone, String text, String serviceName, int daysRemaining) {
        try {
            String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", twilioAccountSid);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(twilioAccountSid, twilioAuthToken);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            
            if (messagingServiceSid != null && !messagingServiceSid.isBlank()) {
                body.add("MessagingServiceSid", messagingServiceSid);
            } else {
                body.add("From", "whatsapp:" + twilioFromNumber);
            }
            
            body.add("To", "whatsapp:" + toPhone);

            if (twilioContentSid != null && !twilioContentSid.isBlank()) {
                body.add("ContentSid", twilioContentSid);
                body.add("ContentVariables", String.format("{\"1\":\"%s\",\"2\":\"%d days\"}", serviceName, daysRemaining));
            } else {
                body.add("Body", text);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("WhatsApp alert dispatched via Twilio to {} for '{}'", toPhone, serviceName);
            } else {
                log.warn("Twilio WhatsApp returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("21654")) {
                log.error("Twilio Sandbox Notice (21654): You must first send the sandbox join keyword (e.g. 'join <your-code>') from your WhatsApp (+{}) to +1 415 523 8886 to open the 24-hour testing window.", toPhone);
                throw new RuntimeException("Twilio WhatsApp session expired/not joined: Send 'join <your-sandbox-keyword>' from your phone to +1 415 523 8886 first.", e);
            }
            log.error("Failed to send WhatsApp alert via Twilio: {}", errorMsg);
            throw new RuntimeException("WhatsApp delivery failed: " + errorMsg, e);
        }
    }

    private void sendViaCallMeBot(String toPhone, String text, String serviceName) {
        try {
            // CallMeBot requires numbers without the '+' sign
            String cleanPhone = toPhone.replace("+", "").trim();
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

            String url = UriComponentsBuilder.fromUriString("https://api.callmebot.com/whatsapp.php")
                    .queryParam("phone", cleanPhone)
                    .queryParam("text", encodedText)
                    .queryParam("apikey", callmebotApiKey)
                    .build(true).toUriString();

            restTemplate.getForObject(url, String.class);
            log.info("WhatsApp alert dispatched via CallMeBot to {} for '{}'", toPhone, serviceName);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp alert via CallMeBot: {}", e.getMessage());
            throw new RuntimeException("WhatsApp delivery failed: " + e.getMessage(), e);
        }
    }

    private String formatPhoneNumber(String raw) {
        String cleaned = raw.replaceAll("[^0-9+]", "").trim();
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }
        return cleaned;
    }

    private String buildWhatsAppMessage(User user, Subscription subscription, int daysRemaining) {
        String header = daysRemaining == 0
                ? "🚨 *SubPulse: RENEWS TODAY!*"
                : "🔔 *SubPulse Renewal Alert* (In " + daysRemaining + " days)";

        return """
                %s
                
                Hi *%s*,
                Your subscription is coming up for renewal:
                
                📦 *Service:* %s
                💰 *Amount:* %s %s
                📅 *Next Billing:* %s
                ⏳ *Remaining:* %d day(s)
                %s
                — _Sent via SubPulse Risk Engine_
                """.formatted(
                header,
                user.getFullName(),
                subscription.getServiceName(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getNextBillingDate(),
                daysRemaining,
                subscription.getWebsiteUrl() != null ? "\n🌐 " + subscription.getWebsiteUrl() : ""
        ).trim();
    }
}
