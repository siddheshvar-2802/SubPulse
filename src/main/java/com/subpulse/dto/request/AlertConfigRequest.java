package com.subpulse.dto.request;

import com.subpulse.enums.NotificationChannel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertConfigRequest {

    @NotNull(message = "Days before is required")
    @Min(value = 1, message = "Days before must be at least 1")
    @Max(value = 30, message = "Days before cannot exceed 30")
    private Integer daysBefore;

    @NotNull(message = "Notification channel is required")
    private NotificationChannel channel;

    private Boolean isEnabled = true;

    /** Telegram chat ID, Discord webhook URL, or custom HTTP webhook URL. */
    private String destination;
}
