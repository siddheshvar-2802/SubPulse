package com.subpulse.service;

import com.subpulse.dto.request.AlertConfigRequest;
import com.subpulse.dto.response.AlertConfigResponse;

import java.util.List;

public interface AlertConfigService {
    AlertConfigResponse create(Long userId, Long subscriptionId, AlertConfigRequest request);
    List<AlertConfigResponse> getBySubscription(Long userId, Long subscriptionId);
    AlertConfigResponse update(Long userId, Long alertConfigId, AlertConfigRequest request);
    void delete(Long userId, Long alertConfigId);
}
