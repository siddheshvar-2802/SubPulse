package com.subpulse.service;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.BillingCycle;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.exception.UnauthorizedAccessException;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.repository.UserRepository;
import com.subpulse.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates RFC 5545 compliant iCalendar (.ics) feeds
 * for subscription renewals, compatible with Google Calendar,
 * Apple Calendar, and Microsoft Outlook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarFeedService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository         userRepository;
    private final JwtUtils               jwtUtils;

    private static final DateTimeFormatter ICS_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional(readOnly = true)
    public String generateIcsFeed(String token) {
        if (token == null || !jwtUtils.isTokenValid(token)) {
            throw new UnauthorizedAccessException("Invalid or expired calendar feed token");
        }

        String email = jwtUtils.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));

        List<Subscription> subscriptions = subscriptionRepository.findByUserIdAndIsActiveTrue(user.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//SubPulse//SaaS Renewal Engine//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:SubPulse Subscriptions (").append(escapeIcs(user.getFullName())).append(")\r\n");
        sb.append("X-WR-TIMEZONE:UTC\r\n");
        sb.append("REFRESH-INTERVAL;VALUE=DURATION:PT12H\r\n");
        sb.append("X-PUBLISHED-TTL:PT12H\r\n");

        for (Subscription sub : subscriptions) {
            appendSubscriptionEvent(sb, sub, user);
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private void appendSubscriptionEvent(StringBuilder sb, Subscription sub, User user) {
        LocalDate billingDate = sub.getNextBillingDate();
        if (billingDate == null) return;

        LocalDate endDate = billingDate.plusDays(1); // All-day event end date is exclusive next day
        String startDateStr = billingDate.format(ICS_DATE_FMT);
        String endDateStr = endDate.format(ICS_DATE_FMT);
        String uid = "subpulse-sub-" + sub.getId() + "-" + user.getId() + "@subpulse.app";

        String summary = "[Renewal] " + sub.getServiceName() + " (" + sub.getCurrency() + " " + sub.getAmount() + ")";
        
        StringBuilder desc = new StringBuilder();
        desc.append("📦 Service: ").append(sub.getServiceName()).append("\\n");
        desc.append("💰 Amount: ").append(sub.getCurrency()).append(" ").append(sub.getAmount()).append("\\n");
        desc.append("🔄 Cycle: ").append(sub.getBillingCycle()).append("\\n");
        desc.append("🏷️ Category: ").append(sub.getCategory()).append("\\n");
        if (sub.getTrialEndDate() != null) {
            desc.append("⏳ Trial Ends: ").append(sub.getTrialEndDate()).append("\\n");
        }
        if (sub.getWebsiteUrl() != null && !sub.getWebsiteUrl().isBlank()) {
            desc.append("🌐 Manage: ").append(sub.getWebsiteUrl()).append("\\n");
        }
        desc.append("— Synced automatically via SubPulse Risk Engine");

        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid).append("\r\n");
        sb.append("DTSTAMP:").append(LocalDate.now().format(ICS_DATE_FMT)).append("T000000Z\r\n");
        sb.append("DTSTART;VALUE=DATE:").append(startDateStr).append("\r\n");
        sb.append("DTEND;VALUE=DATE:").append(endDateStr).append("\r\n");
        sb.append("SUMMARY:").append(escapeIcs(summary)).append("\r\n");
        sb.append("DESCRIPTION:").append(escapeIcs(desc.toString())).append("\r\n");
        sb.append("STATUS:CONFIRMED\r\n");
        sb.append("TRANSP:TRANSPARENT\r\n");

        // Add Recurrence Rule if applicable
        if (sub.getBillingCycle() == BillingCycle.MONTHLY) {
            sb.append("RRULE:FREQ=MONTHLY;INTERVAL=1\r\n");
        } else if (sub.getBillingCycle() == BillingCycle.ANNUAL) {
            sb.append("RRULE:FREQ=YEARLY;INTERVAL=1\r\n");
        } else if (sub.getBillingCycle() == BillingCycle.WEEKLY) {
            sb.append("RRULE:FREQ=WEEKLY;INTERVAL=1\r\n");
        } else if (sub.getBillingCycle() == BillingCycle.QUARTERLY) {
            sb.append("RRULE:FREQ=MONTHLY;INTERVAL=3\r\n");
        }

        // 7-day advance reminder alarm
        sb.append("BEGIN:VALARM\r\n");
        sb.append("TRIGGER:-P7D\r\n");
        sb.append("ACTION:DISPLAY\r\n");
        sb.append("DESCRIPTION:Reminder: ").append(escapeIcs(sub.getServiceName())).append(" renews in 7 days\r\n");
        sb.append("END:VALARM\r\n");

        // 1-day advance reminder alarm
        sb.append("BEGIN:VALARM\r\n");
        sb.append("TRIGGER:-P1D\r\n");
        sb.append("ACTION:DISPLAY\r\n");
        sb.append("DESCRIPTION:Urgent: ").append(escapeIcs(sub.getServiceName())).append(" renews tomorrow!\r\n");
        sb.append("END:VALARM\r\n");

        sb.append("END:VEVENT\r\n");
    }

    private String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,");
    }
}
