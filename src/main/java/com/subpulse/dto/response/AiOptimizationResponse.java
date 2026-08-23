package com.subpulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary of all AI cost optimizations and algorithmic health score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOptimizationResponse {

    private int healthScore;                       // 0 - 100
    private String healthStatus;                   // EXCELLENT, GOOD, FAIR, NEEDS_ATTENTION
    private BigDecimal totalPotentialAnnualSavings;
    private BigDecimal totalPotentialMonthlySavings;
    private String currency;
    private int recommendationCount;
    private List<AiRecommendationDto> recommendations;
}
