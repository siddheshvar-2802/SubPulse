package com.subpulse.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration for SubPulse event-driven architecture.
 * Configures topics, retry policies, and Dead Letter Queue (DLQ) recovery.
 */
@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String RENEWAL_ALERTS_TOPIC = "subpulse.renewal-alerts";
    public static final String RENEWAL_ALERTS_DLT = "subpulse.renewal-alerts.DLT";

    @Bean
    public NewTopic renewalAlertsTopic() {
        return TopicBuilder.name(RENEWAL_ALERTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic renewalAlertsDlt() {
        return TopicBuilder.name(RENEWAL_ALERTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Default error handler: Retries processing 3 times with a 2-second interval,
     * then forwards fatal / unhandled failures to the Dead Letter Topic (.DLT).
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        return new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template),
                new FixedBackOff(2000L, 3L)
        );
    }
}
