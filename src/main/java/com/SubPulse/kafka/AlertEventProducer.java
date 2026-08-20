package com.subpulse.kafka;

import com.subpulse.config.KafkaConfig;
import com.subpulse.dto.event.RenewalAlertEvent;
import com.subpulse.entity.Subscription;
import com.subpulse.notification.NotificationService;
import com.subpulse.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Producer service that publishes subscription renewal alert events to Kafka.
 * Includes graceful fallback to direct dispatch if Kafka broker is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SubscriptionRepository        subscriptionRepository;
    private final NotificationService           notificationService;

    /**
     * Publishes a renewal alert event to Kafka.
     * If Kafka is offline, automatically falls back to direct async alert dispatch.
     */
    public void publishAlertEvent(RenewalAlertEvent event) {
        String key = String.valueOf(event.getSubscriptionId());
        log.info("Publishing RenewalAlertEvent to topic '{}' [Key: {}, EventId: {}, Service: {}, DaysRemaining: {}]",
                KafkaConfig.RENEWAL_ALERTS_TOPIC, key, event.getEventId(), event.getServiceName(), event.getDaysRemaining());

        try {
            kafkaTemplate.send(KafkaConfig.RENEWAL_ALERTS_TOPIC, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully sent event [{}] to partition {} at offset {}",
                                    event.getEventId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.warn("Kafka publish failed asynchronously, executing fallback dispatch: {}", ex.getMessage());
                            executeFallbackDispatch(event);
                        }
                    });
        } catch (Exception ex) {
            log.warn("Kafka broker unreachable ({}). Triggering direct fallback notification dispatch...", ex.getMessage());
            executeFallbackDispatch(event);
        }
    }

    private void executeFallbackDispatch(RenewalAlertEvent event) {
        try {
            Subscription subscription = subscriptionRepository.findById(event.getSubscriptionId()).orElse(null);
            if (subscription != null && Boolean.TRUE.equals(subscription.getIsActive())) {
                notificationService.dispatch(subscription, event.getDaysRemaining());
                log.info("Direct fallback notification dispatched successfully for '{}'", event.getServiceName());
            }
        } catch (Exception e) {
            log.error("Fallback notification dispatch failed: {}", e.getMessage(), e);
        }
    }
}

