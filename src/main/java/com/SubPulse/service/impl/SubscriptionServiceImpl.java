package com.subpulse.service.impl;

import com.subpulse.dto.request.SubscriptionRequest;
import com.subpulse.dto.response.AlertConfigResponse;
import com.subpulse.dto.response.AnalyticsResponse;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.exception.UnauthorizedAccessException;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.repository.UserRepository;
import com.subpulse.service.CurrencyConversionService;
import com.subpulse.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository    subscriptionRepository;
    private final UserRepository            userRepository;
    private final CurrencyConversionService currencyConversionService;

    @Override
    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionRequest request) {
        User user = getUserOrThrow(userId);

        Subscription subscription = Subscription.builder()
                .serviceName(request.getServiceName())
                .description(request.getDescription())
                .websiteUrl(request.getWebsiteUrl())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .billingCycle(request.getBillingCycle())
                .startDate(request.getStartDate())
                .nextBillingDate(request.getNextBillingDate())
                .trialEndDate(request.getTrialEndDate())
                .autoRenew(request.getAutoRenew())
                .category(request.getCategory())
                .user(user)
                .build();

        return mapToResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getById(Long userId, Long subscriptionId) {
        return mapToResponse(getSubscriptionAndVerifyOwnership(userId, subscriptionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAllByUser(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getUpcomingRenewals(Long userId, int days) {
        LocalDate today  = LocalDate.now();
        LocalDate future = today.plusDays(days);
        return subscriptionRepository.findUpcomingRenewals(userId, today, future)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubscriptionResponse update(Long userId, Long subscriptionId, SubscriptionRequest request) {
        Subscription subscription = getSubscriptionAndVerifyOwnership(userId, subscriptionId);

        subscription.setServiceName(request.getServiceName());
        subscription.setDescription(request.getDescription());
        subscription.setWebsiteUrl(request.getWebsiteUrl());
        subscription.setAmount(request.getAmount());
        subscription.setCurrency(request.getCurrency());
        subscription.setBillingCycle(request.getBillingCycle());
        subscription.setStartDate(request.getStartDate());
        subscription.setNextBillingDate(request.getNextBillingDate());
        subscription.setTrialEndDate(request.getTrialEndDate());
        subscription.setAutoRenew(request.getAutoRenew());
        subscription.setCategory(request.getCategory());

        return mapToResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long subscriptionId) {
        Subscription subscription = getSubscriptionAndVerifyOwnership(userId, subscriptionId);
        subscriptionRepository.delete(subscription);
        log.info("Deleted subscription {} for user {}", subscriptionId, userId);
    }

    @Override
    @Transactional
    public SubscriptionResponse toggleActive(Long userId, Long subscriptionId) {
        Subscription subscription = getSubscriptionAndVerifyOwnership(userId, subscriptionId);
        subscription.setIsActive(!subscription.getIsActive());
        return mapToResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(Long userId) {
        return getAnalytics(userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(Long userId, String targetCurrency) {
        User user = getUserOrThrow(userId);
        String finalCurrency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.trim().toUpperCase()
                : (user.getPreferredCurrency() != null ? user.getPreferredCurrency() : "USD");

        List<Subscription> active = subscriptionRepository.findByUserIdAndIsActiveTrue(userId);
        LocalDate today = LocalDate.now();

        BigDecimal monthlySpend = active.stream()
                .map(s -> {
                    BigDecimal monthlyInOriginal = normalizeToMonthly(s.getAmount(), s.getBillingCycle());
                    return currencyConversionService.convert(monthlyInOriginal, s.getCurrency(), finalCurrency);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal annualSpend = monthlySpend.multiply(BigDecimal.valueOf(12));

        Map<String, BigDecimal> byCategory = active.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                s -> {
                                    BigDecimal monthlyInOriginal = normalizeToMonthly(s.getAmount(), s.getBillingCycle());
                                    return currencyConversionService.convert(monthlyInOriginal, s.getCurrency(), finalCurrency);
                                },
                                BigDecimal::add)
                ));

        long renewingIn7  = active.stream()
                .filter(s -> !s.getNextBillingDate().isBefore(today) &&
                             !s.getNextBillingDate().isAfter(today.plusDays(7))).count();
        long renewingIn30 = active.stream()
                .filter(s -> !s.getNextBillingDate().isBefore(today) &&
                             !s.getNextBillingDate().isAfter(today.plusDays(30))).count();

        return AnalyticsResponse.builder()
                .totalActiveSubscriptions(active.size())
                .monthlySpend(monthlySpend)
                .annualSpend(annualSpend)
                .spendByCategory(byCategory)
                .renewingInNextSevenDays((int) renewingIn7)
                .renewingInNextThirtyDays((int) renewingIn30)
                .currency(finalCurrency)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Subscription getSubscriptionAndVerifyOwnership(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        if (!subscription.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("You do not have access to this subscription");
        }
        return subscription;
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private BigDecimal normalizeToMonthly(BigDecimal amount, com.subpulse.enums.BillingCycle cycle) {
        return switch (cycle) {
            case DAILY       -> amount.multiply(BigDecimal.valueOf(30));
            case WEEKLY      -> amount.multiply(BigDecimal.valueOf(4.33));
            case MONTHLY     -> amount;
            case QUARTERLY   -> amount.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
            case SEMI_ANNUAL -> amount.divide(BigDecimal.valueOf(6), 2, java.math.RoundingMode.HALF_UP);
            case ANNUAL      -> amount.divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
            default          -> BigDecimal.ZERO;
        };
    }

    private SubscriptionResponse mapToResponse(Subscription s) {
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), s.getNextBillingDate());

        List<AlertConfigResponse> alerts = s.getAlertConfigs().stream()
                .map(a -> AlertConfigResponse.builder()
                        .id(a.getId())
                        .daysBefore(a.getDaysBefore())
                        .channel(a.getChannel())
                        .isEnabled(a.getIsEnabled())
                        .destination(a.getDestination())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SubscriptionResponse.builder()
                .id(s.getId())
                .serviceName(s.getServiceName())
                .description(s.getDescription())
                .websiteUrl(s.getWebsiteUrl())
                .amount(s.getAmount())
                .currency(s.getCurrency())
                .billingCycle(s.getBillingCycle())
                .startDate(s.getStartDate())
                .nextBillingDate(s.getNextBillingDate())
                .trialEndDate(s.getTrialEndDate())
                .isActive(s.getIsActive())
                .autoRenew(s.getAutoRenew())
                .category(s.getCategory())
                .daysUntilRenewal(daysUntil)
                .alertConfigs(alerts)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
