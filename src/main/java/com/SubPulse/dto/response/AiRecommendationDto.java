package com.subpulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents an individual AI-driven cost optimization recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationDto {

    private String id;
    private String type;            // DUPLICATE_SERVICE, ANNUAL_SAVINGS, EXPIRING_TRIAL, HIGH_SPEND
    private String title;
    private String description;
    private String category;
    private List<String> serviceNames;
    private BigDecimal potentialMonthlySavings;
    private BigDecimal potentialAnnualSavings;
    private String currency;
    private String impactLevel;     // HIGH, MEDIUM, LOW
    private String actionUrl;
    private String actionLabel;
}
