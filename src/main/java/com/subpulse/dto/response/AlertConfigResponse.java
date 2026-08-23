package com.subpulse.dto.response;

import com.subpulse.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertConfigResponse {

    private Long id;
    private Integer daysBefore;
    private NotificationChannel channel;
    private Boolean isEnabled;
    private String destination;
    private LocalDateTime createdAt;
}
