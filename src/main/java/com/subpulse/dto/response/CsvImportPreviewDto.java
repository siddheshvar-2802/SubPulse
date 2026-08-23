package com.subpulse.dto.response;

import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
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
public class CsvImportPreviewDto {

    /** Unique temp ID for preview table referencing. */
    private String tempId;

    /** Clean detected service name (e.g. "Netflix", "AWS", "ChatGPT Plus"). */
    private String serviceName;

    /** Raw transaction line from statement. */
    private String rawDescription;

    /** Detected cost amount. */
    private BigDecimal amount;

    /** Currency code (e.g. "USD", "INR", "EUR"). */
    private String currency;

    /** Inferred category. */
    private SubscriptionCategory category;

    /** Inferred billing cycle (e.g. MONTHLY, ANNUAL). */
    private BillingCycle billingCycle;

    /** Original transaction date. */
    private LocalDate transactionDate;

    /** Estimated next billing / renewal date. */
    private LocalDate nextBillingDate;

    /** Official service website URL. */
    private String websiteUrl;

    /** Detection confidence score (e.g. "HIGH", "MEDIUM", "LOW"). */
    private String confidence;

    /** Whether it matches an existing active subscription already in user's account. */
    private boolean isAlreadyTracked;
}
