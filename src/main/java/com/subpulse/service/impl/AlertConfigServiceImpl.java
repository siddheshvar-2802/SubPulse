package com.subpulse.service.impl;

import com.subpulse.dto.request.AlertConfigRequest;
import com.subpulse.dto.response.AlertConfigResponse;
import com.subpulse.entity.AlertConfig;
import com.subpulse.entity.Subscription;
import com.subpulse.exception.DuplicateResourceException;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.exception.UnauthorizedAccessException;
import com.subpulse.repository.AlertConfigRepository;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.service.AlertConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertConfigServiceImpl implements AlertConfigService {

    private final AlertConfigRepository alertConfigRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public AlertConfigResponse create(Long userId, Long subscriptionId, AlertConfigRequest request) {
        Subscription subscription = getSubscriptionAndVerifyOwnership(userId, subscriptionId);

        if (alertConfigRepository.existsBySubscriptionIdAndChannelAndDaysBefore(
                subscriptionId, request.getChannel(), request.getDaysBefore())) {
            throw new DuplicateResourceException(
                    "Alert already exists for this subscription with channel "
                    + request.getChannel() + " and " + request.getDaysBefore() + " days before");
        }

        AlertConfig alertConfig = AlertConfig.builder()
                .daysBefore(request.getDaysBefore())
                .channel(request.getChannel())
                .isEnabled(request.getIsEnabled())
                .destination(request.getDestination())
                .subscription(subscription)
                .build();

        return mapToResponse(alertConfigRepository.save(alertConfig));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertConfigResponse> getBySubscription(Long userId, Long subscriptionId) {
        getSubscriptionAndVerifyOwnership(userId, subscriptionId); // ownership check
        return alertConfigRepository.findBySubscriptionId(subscriptionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AlertConfigResponse update(Long userId, Long alertConfigId, AlertConfigRequest request) {
        AlertConfig alertConfig = alertConfigRepository.findById(alertConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertConfig", alertConfigId));

        verifyAlertOwnership(userId, alertConfig);

        alertConfig.setDaysBefore(request.getDaysBefore());
        alertConfig.setChannel(request.getChannel());
        alertConfig.setIsEnabled(request.getIsEnabled());
        alertConfig.setDestination(request.getDestination());

        return mapToResponse(alertConfigRepository.save(alertConfig));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long alertConfigId) {
        AlertConfig alertConfig = alertConfigRepository.findById(alertConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertConfig", alertConfigId));
        verifyAlertOwnership(userId, alertConfig);
        alertConfigRepository.delete(alertConfig);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Subscription getSubscriptionAndVerifyOwnership(Long userId, Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));
        if (!sub.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("You do not have access to this subscription");
        }
        return sub;
    }

    private void verifyAlertOwnership(Long userId, AlertConfig alertConfig) {
        if (!alertConfig.getSubscription().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("You do not have access to this alert config");
        }
    }

    private AlertConfigResponse mapToResponse(AlertConfig a) {
        return AlertConfigResponse.builder()
                .id(a.getId())
                .daysBefore(a.getDaysBefore())
                .channel(a.getChannel())
                .isEnabled(a.getIsEnabled())
                .destination(a.getDestination())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
