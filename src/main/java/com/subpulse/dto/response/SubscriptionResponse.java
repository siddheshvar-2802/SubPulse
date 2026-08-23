package com.subpulse.dto.response;

import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private Long id;
    private String serviceName;
    private String description;
    private String websiteUrl;
    private BigDecimal amount;
    private String currency;
    private BillingCycle billingCycle;
    private LocalDate startDate;
    private LocalDate nextBillingDate;
    private LocalDate trialEndDate;
    private Boolean isActive;
    private Boolean autoRenew;
    private SubscriptionCategory category;

    /** Days until next billing date — computed on the fly for the frontend. */
    private Long daysUntilRenewal;

    private List<AlertConfigResponse> alertConfigs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
