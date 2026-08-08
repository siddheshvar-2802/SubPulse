package com.subpulse.scheduler;

import com.subpulse.entity.Subscription;
import com.subpulse.notification.NotificationService;
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
import java.util.Arrays;
import java.util.List;

/**
 * Daily background job that fires renewal alerts.
 *
 * Runs every day at 09:00 AM UTC.
 * Uses ShedLock to prevent duplicate execution across multiple server instances.
 *
 * Logic:
 *  For each configured alert window [30, 14, 7, 3, 1, 0 days]:
 *    → Find subscriptions whose nextBillingDate == today + N days
 *    → Skip if a SENT notification already exists for this subscription + days_remaining
 *    → Dispatch alert via NotificationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenewalAlertScheduler {

    private static final List<Integer> ALERT_WINDOWS = Arrays.asList(30, 14, 7, 3, 1, 0);

    private final SubscriptionRepository    subscriptionRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService       notificationService;

    @Scheduled(cron = "0 0 9 * * *") // Every day at 09:00 AM UTC
    @SchedulerLock(
            name = "renewalAlertScheduler",
            lockAtLeastFor = "PT5M",   // Hold lock for at least 5 minutes
            lockAtMostFor  = "PT30M"   // Release lock after 30 minutes max
    )
    @Transactional
    public void processRenewalAlerts() {
        log.info("=== RenewalAlertScheduler started ===");
        int totalAlertsSent = 0;

        for (int daysRemaining : ALERT_WINDOWS) {
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
                    notificationService.dispatch(subscription, daysRemaining);
                    totalAlertsSent++;
                } catch (Exception e) {
                    log.error("Error dispatching alert for subscription '{}': {}",
                            subscription.getServiceName(), e.getMessage(), e);
                }
            }
        }

        log.info("=== RenewalAlertScheduler completed — {} alert(s) dispatched ===", totalAlertsSent);
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
