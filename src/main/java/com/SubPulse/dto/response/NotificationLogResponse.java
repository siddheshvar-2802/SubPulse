package com.subpulse.dto.response;

import com.subpulse.enums.NotificationChannel;
import com.subpulse.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {

    private Long id;
    private Long subscriptionId;
    private String subscriptionName;
    private NotificationChannel channel;
    private NotificationStatus status;
    private LocalDateTime sentAt;
    private Integer daysRemaining;
    private String errorMessage;
    private LocalDateTime createdAt;
}
