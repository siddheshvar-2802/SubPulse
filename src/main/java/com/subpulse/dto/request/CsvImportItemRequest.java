package com.subpulse.dto.request;

import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportItemRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String currency;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;

    private SubscriptionCategory category;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Next billing date is required")
    private LocalDate nextBillingDate;

    private String websiteUrl;

    private String description;
}
