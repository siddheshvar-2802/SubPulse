package com.subpulse.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event payload dispatched to Kafka when a subscription renewal alert is triggered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewalAlertEvent implements Serializable {

    private String eventId;
    private Long subscriptionId;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String serviceName;
    private BigDecimal amount;
    private String currency;
    private LocalDate nextBillingDate;
    private Integer daysRemaining;
    private LocalDateTime timestamp;
}
