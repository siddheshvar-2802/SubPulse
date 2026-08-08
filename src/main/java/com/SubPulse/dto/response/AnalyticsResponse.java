package com.subpulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    /** Total active subscriptions count. */
    private int totalActiveSubscriptions;

    /** Total monthly spend in user's preferred currency. */
    private BigDecimal monthlySpend;

    /** Total annual spend (projected) in user's preferred currency. */
    private BigDecimal annualSpend;

    /** Spend breakdown by category name. */
    private Map<String, BigDecimal> spendByCategory;

    /** Number of subscriptions renewing in the next 7 days. */
    private int renewingInNextSevenDays;

    /** Number of subscriptions renewing in the next 30 days. */
    private int renewingInNextThirtyDays;

    /** User's preferred currency code. */
    private String currency;
}
