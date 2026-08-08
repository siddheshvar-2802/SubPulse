package com.subpulse.entity;

import com.subpulse.enums.NotificationChannel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Defines WHEN and HOW a user wants to be notified about a subscription renewal.
 *
 * Example: "Alert me via EMAIL 7 days before Netflix renewal."
 * Example: "Alert me via TELEGRAM 1 day before GitHub Pro renewal."
 *
 * One subscription can have multiple AlertConfigs
 * (e.g., EMAIL at 7 days AND Telegram at 1 day).
 */
@Entity
@Table(
    name = "alert_configs",
    indexes = {
        @Index(name = "idx_alert_subscription_id", columnList = "subscription_id"),
        @Index(name = "idx_alert_channel",         columnList = "channel")
    },
    uniqueConstraints = {
        // Prevent duplicate: same subscription + same channel + same days_before
        @UniqueConstraint(
            name  = "uq_alert_sub_channel_days",
            columnNames = {"subscription_id", "channel", "days_before"}
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertConfig extends BaseEntity {

    /**
     * How many days BEFORE the billing date this alert fires.
     * Allowed range: 1–30 days.
     */
    @NotNull
    @Min(1)
    @Max(30)
    @Column(name = "days_before", nullable = false)
    private Integer daysBefore;

    /** The channel to deliver this alert through. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /** Allows muting one channel temporarily without deleting the config. */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    /**
     * Telegram chat ID, Discord webhook URL, or custom HTTP webhook URL.
     * Stored per-config so different subscriptions can alert different places.
     */
    @Column(name = "destination", length = 500)
    private String destination;

    // ── Relationship ──────────────────────────────────────────────────────────

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;
}
