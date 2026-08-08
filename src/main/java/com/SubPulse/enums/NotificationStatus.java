package com.subpulse.enums;

/**
 * Status of a notification attempt logged in NotificationLog.
 */
public enum NotificationStatus {
    PENDING,  // Queued but not yet sent
    SENT,     // Successfully delivered
    FAILED,   // Delivery failed (check error_message)
    SKIPPED   // Skipped (e.g. user disabled alerts)
}
