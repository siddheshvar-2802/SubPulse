package com.subpulse.scheduler;

import com.subpulse.dto.event.RenewalAlertEvent;
import com.subpulse.entity.Subscription;
import com.subpulse.kafka.AlertEventProducer;
import com.subpulse.repository.AlertConfigRepository;
import com.subpulse.repository.NotificationLogRepository;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Daily background job that fires renewal alerts by publishing events to Kafka.
 *
 * Runs every day at 09:00 AM UTC.
 * Uses ShedLock to prevent duplicate execution across multiple server instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenewalAlertScheduler {

    private static final List<Integer> ALERT_WINDOWS = Arrays.asList(30, 14, 7, 5, 3, 2, 1, 0);

    private final SubscriptionRepository    subscriptionRepository;
    private final AlertConfigRepository alertConfigRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final AlertEventProducer        alertEventProducer;

    @Scheduled(cron = "${app.scheduler.renewal-alert-cron:0 0 9 * * *}") // Every day at 09:00 AM UTC
    @SchedulerLock(
            name = "renewalAlertScheduler",
            lockAtLeastFor = "PT5M",   // Hold lock for at least 5 minutes
            lockAtMostFor  = "PT30M"   // Release lock after 30 minutes max
    )
    @Transactional(readOnly = true)
    public void processRenewalAlerts() {
        log.info("=== RenewalAlertScheduler started (Kafka Streaming Mode) ===");
        int totalEventsPublished = 0;

        java.util.Set<Integer> targetWindows = new java.util.TreeSet<>(ALERT_WINDOWS);
        try {
            alertConfigRepository.findAll().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()) && a.getDaysBefore() != null)
                    .map(com.subpulse.entity.AlertConfig::getDaysBefore)
                    .forEach(targetWindows::add);
        } catch (Exception e) {
            log.warn("Could not query dynamic alert windows, falling back to default windows: {}", e.getMessage());
        }

        for (int daysRemaining : targetWindows) {
            LocalDate targetDate = LocalDate.now().plusDays(daysRemaining);

            List<Subscription> dueSubscriptions =
                    subscriptionRepository.findByNextBillingDateAndIsActiveTrue(targetDate);

            log.info("Checking {} day(s) window — found {} subscription(s) renewing on {}",
                    daysRemaining, dueSubscriptions.size(), targetDate);

            for (Subscription subscription : dueSubscriptions) {
                if (alreadyNotified(subscription.getId(), daysRemaining)) {
                    log.debug("Skipping '{}' — already notified for {} days window",
                            subscription.getServiceName(), daysRemaining);
                    continue;
                }

                try {
                    RenewalAlertEvent event = RenewalAlertEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .subscriptionId(subscription.getId())
                            .userId(subscription.getUser().getId())
                            .userFullName(subscription.getUser().getFullName())
                            .userEmail(subscription.getUser().getEmail())
                            .serviceName(subscription.getServiceName())
                            .amount(subscription.getAmount())
                            .currency(subscription.getCurrency())
                            .nextBillingDate(subscription.getNextBillingDate())
                            .daysRemaining(daysRemaining)
                            .timestamp(LocalDateTime.now())
                            .build();

                    alertEventProducer.publishAlertEvent(event);
                    totalEventsPublished++;
                } catch (Exception e) {
                    log.error("Error publishing alert event for subscription '{}': {}",
                            subscription.getServiceName(), e.getMessage(), e);
                }
            }
        }

        log.info("=== RenewalAlertScheduler completed — {} event(s) published to Kafka ===", totalEventsPublished);
    }

    /**
     * Guard against re-sending the same alert on retry or restart.
     * Returns true if a SENT notification already exists for this subscription + daysRemaining.
     */
    private boolean alreadyNotified(Long subscriptionId, int daysRemaining) {
        return notificationLogRepository.existsBySubscriptionIdAndDaysRemainingAndStatus(
                subscriptionId, daysRemaining, NotificationStatus.SENT);
    }
}
