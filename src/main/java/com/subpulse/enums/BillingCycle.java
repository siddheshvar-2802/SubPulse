package com.subpulse.enums;

/**
 * Billing cycle types supported by SubPulse.
 */
public enum BillingCycle {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,    // Every 3 months
    SEMI_ANNUAL,  // Every 6 months
    ANNUAL,
    LIFETIME,     // One-time payment, no recurrence
    TRIAL         // Free trial — will convert or expire
}
