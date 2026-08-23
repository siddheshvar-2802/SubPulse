package com.subpulse.controller;

import com.subpulse.dto.request.AlertConfigRequest;
import com.subpulse.dto.response.AlertConfigResponse;
import com.subpulse.security.CustomUserDetails;
import com.subpulse.service.AlertConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions/{subscriptionId}/alerts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alert Configs", description = "Manage notification alert rules for subscriptions")
public class AlertConfigController {

    private final AlertConfigService alertConfigService;

    @PostMapping
    @Operation(summary = "Add an alert rule to a subscription")
    public ResponseEntity<AlertConfigResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody AlertConfigRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertConfigService.create(userDetails.getId(), subscriptionId, request));
    }

    @GetMapping
    @Operation(summary = "Get all alert rules for a subscription")
    public ResponseEntity<List<AlertConfigResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long subscriptionId) {

        return ResponseEntity.ok(alertConfigService.getBySubscription(userDetails.getId(), subscriptionId));
    }

    @PutMapping("/{alertId}")
    @Operation(summary = "Update an alert rule")
    public ResponseEntity<AlertConfigResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long subscriptionId,
            @PathVariable Long alertId,
            @Valid @RequestBody AlertConfigRequest request) {

        return ResponseEntity.ok(alertConfigService.update(userDetails.getId(), alertId, request));
    }

    @DeleteMapping("/{alertId}")
    @Operation(summary = "Delete an alert rule")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long subscriptionId,
            @PathVariable Long alertId) {

        alertConfigService.delete(userDetails.getId(), alertId);
        return ResponseEntity.noContent().build();
    }
}
