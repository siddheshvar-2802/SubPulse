package com.subpulse.service;

import com.subpulse.dto.response.AiOptimizationResponse;
import com.subpulse.dto.response.AiRecommendationDto;
import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI & Smart Cost Optimization Service.
 * Analyzes active subscriptions for redundancy, billing plan arbitrage, and trial risks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOptimizationService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository         userRepository;
    private final CurrencyConversionService currencyConversionService;

    @Transactional(readOnly = true)
    public AiOptimizationResponse analyzeSubscriptions(Long userId, String targetCurrency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String currency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.toUpperCase().trim()
                : (user.getPreferredCurrency() != null ? user.getPreferredCurrency().toUpperCase().trim() : "USD");

        List<Subscription> activeSubs = subscriptionRepository.findByUserId(userId).stream()
                .filter(Subscription::getIsActive)
                .toList();

        if (activeSubs.isEmpty()) {
            return AiOptimizationResponse.builder()
                    .healthScore(100)
                    .healthStatus("EXCELLENT")
                    .totalPotentialAnnualSavings(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .totalPotentialMonthlySavings(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .currency(currency)
                    .recommendationCount(0)
                    .recommendations(Collections.emptyList())
                    .build();
        }

        List<AiRecommendationDto> recommendations = new ArrayList<>();
        int healthScoreDeductions = 0;

        // ── 1. Detect Category Overlaps & Redundancies ────────────────────────
        Map<SubscriptionCategory, List<Subscription>> subsByCategory = activeSubs.stream()
                .collect(Collectors.groupingBy(Subscription::getCategory));

        for (Map.Entry<SubscriptionCategory, List<Subscription>> entry : subsByCategory.entrySet()) {
            SubscriptionCategory category = entry.getKey();
            List<Subscription> subsInCat = entry.getValue();

            if (subsInCat.size() >= 2 && category != SubscriptionCategory.OTHER) {
                // Potential savings from pausing/rotating secondary service
                BigDecimal lowestMonthly = subsInCat.stream()
                        .map(s -> normalizeToMonthly(s, currency))
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                BigDecimal annualSavings = lowestMonthly.multiply(BigDecimal.valueOf(12))
                        .setScale(2, RoundingMode.HALF_UP);

                List<String> names = subsInCat.stream().map(Subscription::getServiceName).toList();

                recommendations.add(AiRecommendationDto.builder()
                        .id("overlap-" + category.name().toLowerCase())
                        .type("DUPLICATE_SERVICE")
                        .title("Overlapping " + formatCategoryName(category) + " Subscriptions")
                        .description("You have " + subsInCat.size() + " active subscriptions in " + formatCategoryName(category) + " (" + String.join(", ", names) + "). Rotating or consolidating them could save significant money.")
                        .category(category.name())
                        .serviceNames(names)
                        .potentialMonthlySavings(lowestMonthly)
                        .potentialAnnualSavings(annualSavings)
                        .currency(currency)
                        .impactLevel("HIGH")
                        .actionLabel("Review Overlap")
                        .build());

                healthScoreDeductions += 15;
            }
        }

        // ── 2. Annual vs Monthly Billing Arbitrage (18% avg savings) ────────
        for (Subscription sub : activeSubs) {
            if (sub.getBillingCycle() == BillingCycle.MONTHLY) {
                BigDecimal monthlyInTarget = normalizeToMonthly(sub, currency);
                if (monthlyInTarget.compareTo(BigDecimal.valueOf(10)) >= 0) {
                    BigDecimal estimatedAnnualSavings = monthlyInTarget.multiply(BigDecimal.valueOf(12))
                            .multiply(BigDecimal.valueOf(0.18))
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal estimatedMonthlySavings = estimatedAnnualSavings.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

                    recommendations.add(AiRecommendationDto.builder()
                            .id("annual-" + sub.getId())
                            .type("ANNUAL_SAVINGS")
                            .title("Switch " + sub.getServiceName() + " to Annual Billing")
                            .description(sub.getServiceName() + " is currently on monthly billing. Annual plans typically offer a 15%–20% discount.")
                            .category(sub.getCategory().name())
                            .serviceNames(List.of(sub.getServiceName()))
                            .potentialMonthlySavings(estimatedMonthlySavings)
                            .potentialAnnualSavings(estimatedAnnualSavings)
                            .currency(currency)
                            .impactLevel("MEDIUM")
                            .actionUrl(sub.getWebsiteUrl())
                            .actionLabel("Switch to Annual")
                            .build());

                    healthScoreDeductions += 5;
                }
            }
        }

        // ── 3. Expiring Free Trials & Risk Guard ─────────────────────────────
        LocalDate today = LocalDate.now();
        LocalDate twoWeeksOut = today.plusDays(14);

        for (Subscription sub : activeSubs) {
            if (sub.getTrialEndDate() != null && !sub.getTrialEndDate().isBefore(today) && !sub.getTrialEndDate().isAfter(twoWeeksOut)) {
                BigDecimal monthlyVal = normalizeToMonthly(sub, currency);
                BigDecimal annualVal = monthlyVal.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);

                recommendations.add(AiRecommendationDto.builder()
                        .id("trial-" + sub.getId())
                        .type("EXPIRING_TRIAL")
                        .title("Free Trial Ending: " + sub.getServiceName())
                        .description("Your free trial for " + sub.getServiceName() + " ends on " + sub.getTrialEndDate() + ". Review usage to avoid unwanted auto-renewals.")
                        .category(sub.getCategory().name())
                        .serviceNames(List.of(sub.getServiceName()))
                        .potentialMonthlySavings(monthlyVal)
                        .potentialAnnualSavings(annualVal)
                        .currency(currency)
                        .impactLevel("HIGH")
                        .actionUrl(sub.getWebsiteUrl())
                        .actionLabel("Manage Trial")
                        .build());

                healthScoreDeductions += 10;
            }
        }

        // ── Compute Aggregate Totals & Health Score ─────────────────────────
        BigDecimal totalAnnualSavings = recommendations.stream()
                .map(AiRecommendationDto::getPotentialAnnualSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalMonthlySavings = totalAnnualSavings.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        int calculatedScore = Math.max(25, Math.min(100, 100 - healthScoreDeductions));
        String healthStatus = calculatedScore >= 85 ? "EXCELLENT" : calculatedScore >= 70 ? "GOOD" : calculatedScore >= 50 ? "FAIR" : "NEEDS_ATTENTION";

        return AiOptimizationResponse.builder()
                .healthScore(calculatedScore)
                .healthStatus(healthStatus)
                .totalPotentialAnnualSavings(totalAnnualSavings)
                .totalPotentialMonthlySavings(totalMonthlySavings)
                .currency(currency)
                .recommendationCount(recommendations.size())
                .recommendations(recommendations)
                .build();
    }

    private BigDecimal normalizeToMonthly(Subscription sub, String targetCurrency) {
        if (sub.getAmount() == null) return BigDecimal.ZERO;
        
        BigDecimal rawMonthly = switch (sub.getBillingCycle()) {
            case DAILY       -> sub.getAmount().multiply(BigDecimal.valueOf(30));
            case WEEKLY      -> sub.getAmount().multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case MONTHLY     -> sub.getAmount();
            case QUARTERLY   -> sub.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case SEMI_ANNUAL -> sub.getAmount().divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            case ANNUAL      -> sub.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case LIFETIME, TRIAL -> BigDecimal.ZERO;
        };

        return currencyConversionService.convert(rawMonthly, sub.getCurrency(), targetCurrency);
    }

    private String formatCategoryName(SubscriptionCategory category) {
        String[] words = category.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
