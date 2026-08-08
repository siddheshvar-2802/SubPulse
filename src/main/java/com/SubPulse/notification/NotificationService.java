package com.subpulse.notification;

import com.subpulse.entity.AlertConfig;
import com.subpulse.entity.NotificationLog;
import com.subpulse.entity.Subscription;
import com.subpulse.enums.NotificationChannel;
import com.subpulse.enums.NotificationStatus;
import com.subpulse.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central notification dispatcher.
 * Resolves the correct NotificationProvider by channel and records
 * every delivery attempt in the NotificationLog audit table.
 *
 * Uses constructor injection to auto-collect all NotificationProvider beans.
 * Adding a new channel = implement NotificationProvider + register as @Component.
 * Zero changes needed here. (Open/Closed Principle)
 */
@Slf4j
@Service
public class NotificationService {

    private final Map<NotificationChannel, NotificationProvider> providers;
    private final NotificationLogRepository                      logRepository;

    public NotificationService(
            List<NotificationProvider> providerList,
            NotificationLogRepository logRepository) {

        // Build a map: channel → provider for O(1) lookup
        this.providers   = providerList.stream()
                .collect(Collectors.toMap(NotificationProvider::getChannel, Function.identity()));
        this.logRepository = logRepository;

        log.info("NotificationService initialized with channels: {}", this.providers.keySet());
    }

    /**
     * Dispatch an alert for a subscription via all its enabled alert configs.
     */
    public void dispatch(Subscription subscription, int daysRemaining) {
        List<AlertConfig> enabledAlerts = subscription.getAlertConfigs().stream()
                .filter(AlertConfig::getIsEnabled)
                .toList();

        for (AlertConfig alert : enabledAlerts) {
            sendAndLog(subscription, alert, daysRemaining);
        }
    }

    private void sendAndLog(Subscription subscription, AlertConfig alert, int daysRemaining) {
        NotificationProvider provider = providers.get(alert.getChannel());

        if (provider == null) {
            log.warn("No provider registered for channel: {}", alert.getChannel());
            return;
        }

        NotificationLog.NotificationLogBuilder logBuilder = NotificationLog.builder()
                .subscription(subscription)
                .channel(alert.getChannel())
                .daysRemaining(daysRemaining)
                .sentAt(LocalDateTime.now());

        try {
            provider.sendAlert(
                    subscription.getUser(),
                    subscription,
                    daysRemaining,
                    alert.getDestination()
            );

            logRepository.save(logBuilder
                    .status(NotificationStatus.SENT)
                    .build());

        } catch (Exception e) {
            log.error("Notification failed via {} for subscription '{}': {}",
                    alert.getChannel(), subscription.getServiceName(), e.getMessage());

            logRepository.save(logBuilder
                    .status(NotificationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }
}
