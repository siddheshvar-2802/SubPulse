package com.subpulse.entity;

import com.subpulse.enums.NotificationChannel;
import com.subpulse.enums.NotificationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit log of every notification attempt made by the SubPulse alert engine.
 *
 * This is an APPEND-ONLY table — records are NEVER updated or deleted.
 * It gives users a full delivery history and helps debug failures.
 *
 * Example record:
 *   subscription : "Netflix Monthly"
 *   channel      : EMAIL
 *   status       : SENT
 *   sentAt       : 2026-08-06T09:00:00
 *   daysRemaining: 7
 */
@Entity
@Table(
    name = "notification_logs",
    indexes = {
        @Index(name = "idx_notif_subscription_id", columnList = "subscription_id"),
        @Index(name = "idx_notif_status",          columnList = "status"),
        @Index(name = "idx_notif_sent_at",         columnList = "sent_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseEntity {

    /** The channel used for this delivery attempt. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /** Result of this delivery attempt. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    /** Timestamp when the notification was sent or attempted. */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * Days remaining until billing date when this alert was sent.
     * Stored for audit clarity (e.g., "This was the 7-day advance alert").
     */
    @Column(name = "days_remaining")
    private Integer daysRemaining;

    /**
     * Error details if delivery failed (e.g., SMTP timeout, invalid URL).
     * Null on successful delivery.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** The rendered alert message body, stored for traceability. */
    @Column(name = "message_body", length = 2000)
    private String messageBody;

    // ── Relationship ──────────────────────────────────────────────────────────

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;
}
