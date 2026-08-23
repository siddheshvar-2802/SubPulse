package com.subpulse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered SubPulse user.
 * Supports both standard email/password login and OAuth2 (Google, GitHub).
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    // ── Identity ──────────────────────────────────────────────────────────────

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Hashed password. Nullable for OAuth2-only users (Google/GitHub login).
     */
    @Column(name = "password_hash")
    private String passwordHash;

    // ── OAuth2 ────────────────────────────────────────────────────────────────

    /** OAuth2 provider name e.g. "google", "github". Null for local users. */
    @Column(name = "oauth2_provider", length = 30)
    private String oauth2Provider;


    /** Unique ID returned by OAuth2 provider. Used to match returning users. */
    @Column(name = "oauth2_provider_id", length = 200)
    private String oauth2ProviderId;

    // ── Preferences ───────────────────────────────────────────────────────────

    /** User's timezone for scheduling alerts (e.g., "Asia/Kolkata"). Defaults to UTC. */
    @Column(name = "timezone", nullable = false, length = 60)
    @Builder.Default
    private String timezone = "UTC";

    /** ISO 4217 currency code (e.g., "USD", "INR"). */
    @Column(name = "preferred_currency", nullable = false, length = 10)
    @Builder.Default
    private String preferredCurrency = "USD";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    // ── Relationships ─────────────────────────────────────────────────────────

    /** All subscriptions owned by this user. Cascade delete on user removal. */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Subscription> subscriptions = new ArrayList<>();
}
