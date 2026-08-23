package com.subpulse.entity;

import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single recurring subscription tracked by a user.
 * Examples: Netflix, GitHub Copilot, AWS, Figma, etc.
 */
@Entity
@Table(
    name = "subscriptions",
    indexes = {
        @Index(name = "idx_sub_user_id",          columnList = "user_id"),
        @Index(name = "idx_sub_next_billing_date", columnList = "next_billing_date"),
        @Index(name = "idx_sub_is_active",         columnList = "is_active")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    // ── Core Info ─────────────────────────────────────────────────────────────

    @NotBlank
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    /** Optional notes (e.g., "Team plan for 5 members"). */
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    // ── Billing ───────────────────────────────────────────────────────────────

    /**
     * Cost per billing cycle. Use BigDecimal — NEVER double/float for money.
     * Precision 10, scale 2 supports values up to 99,999,999.99
     */
    @NotNull
    @Positive
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** ISO 4217 currency code (e.g., "USD", "EUR", "INR"). */
    @NotBlank
    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    // ── Dates ─────────────────────────────────────────────────────────────────

    /** Date the subscription started. */
    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Next renewal/billing date. The scheduler uses this to fire alerts. */
    @NotNull
    @Column(name = "next_billing_date", nullable = false)
    private LocalDate nextBillingDate;

    /** For TRIAL billing cycle — the date the trial expires. */
    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    // ── State ─────────────────────────────────────────────────────────────────

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    // ── Classification ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    @Builder.Default
    private SubscriptionCategory category = SubscriptionCategory.OTHER;

    // ── Relationships ─────────────────────────────────────────────────────────

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Alert rules for this subscription (e.g., alert 7 days before renewal). */
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AlertConfig> alertConfigs = new ArrayList<>();

    /** Full notification delivery history for this subscription. */
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationLog> notificationLogs = new ArrayList<>();
}
