package com.subpulse.enums;

/**
 * Notification channels supported for sending renewal alerts.
 */
public enum NotificationChannel {
    EMAIL,
    TELEGRAM,
    @Deprecated
    WHATSAPP,
    DISCORD,
    WEBHOOK
}
