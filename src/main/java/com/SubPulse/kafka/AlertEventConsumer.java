package com.subpulse.kafka;

import com.subpulse.config.KafkaConfig;
import com.subpulse.dto.event.RenewalAlertEvent;
import com.subpulse.entity.Subscription;
import com.subpulse.notification.NotificationService;
import com.subpulse.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer worker that listens for renewal alert events from Kafka
 * and processes notification dispatches asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService    notificationService;

    @Transactional
    @KafkaListener(
            topics = KafkaConfig.RENEWAL_ALERTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:subpulse-group}"
    )
    public void consumeRenewalAlert(@Payload RenewalAlertEvent event) {
        log.info("Received RenewalAlertEvent from Kafka: [EventId: {}, Service: {}, SubId: {}, DaysRemaining: {}]",
                event.getEventId(), event.getServiceName(), event.getSubscriptionId(), event.getDaysRemaining());

        Subscription subscription = subscriptionRepository.findById(event.getSubscriptionId())
                .orElse(null);

        if (subscription == null) {
            log.warn("Subscription ID {} not found. Skipping event {}", event.getSubscriptionId(), event.getEventId());
            return;
        }

        if (!Boolean.TRUE.equals(subscription.getIsActive())) {
            log.info("Subscription '{}' is no longer active. Skipping alert dispatch.", subscription.getServiceName());
            return;
        }

        // Dispatch notifications via enabled channels (Email, Telegram, etc.)
        notificationService.dispatch(subscription, event.getDaysRemaining());
        log.info("Finished processing RenewalAlertEvent [{}] for subscription '{}'",
                event.getEventId(), subscription.getServiceName());
    }

    /**
     * Dead Letter Topic (DLT) listener for permanently failed alert messages.
     */
    @KafkaListener(
            topics = KafkaConfig.RENEWAL_ALERTS_DLT,
            groupId = "${spring.kafka.consumer.group-id:subpulse-group}-dlt"
    )
    public void consumeDeadLetterAlert(@Payload Object failedEvent) {
        log.error("ALERT PERMANENTLY FAILED -> Received message in Dead Letter Topic (DLT): {}", failedEvent);
    }
}
