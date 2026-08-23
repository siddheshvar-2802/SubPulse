package com.subpulse.dto.request;

import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SubscriptionRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    private String description;

    private String websiteUrl;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String currency = "USD";

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Next billing date is required")
    private LocalDate nextBillingDate;

    private LocalDate trialEndDate;

    private Boolean autoRenew = true;

    private SubscriptionCategory category = SubscriptionCategory.OTHER;
}
