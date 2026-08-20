package com.subpulse.service;

import com.subpulse.dto.request.SubscriptionRequest;
import com.subpulse.dto.response.AnalyticsResponse;
import com.subpulse.dto.response.SubscriptionResponse;

import java.util.List;

public interface SubscriptionService {
    SubscriptionResponse create(Long userId, SubscriptionRequest request);
    SubscriptionResponse getById(Long userId, Long subscriptionId);
    List<SubscriptionResponse> getAllByUser(Long userId);
    List<SubscriptionResponse> getUpcomingRenewals(Long userId, int days);
    SubscriptionResponse update(Long userId, Long subscriptionId, SubscriptionRequest request);
    void delete(Long userId, Long subscriptionId);
    SubscriptionResponse toggleActive(Long userId, Long subscriptionId);
    AnalyticsResponse getAnalytics(Long userId);
    AnalyticsResponse getAnalytics(Long userId, String targetCurrency);
}
