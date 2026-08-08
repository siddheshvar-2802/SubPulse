package com.subpulse.controller;

import com.subpulse.dto.request.SubscriptionRequest;
import com.subpulse.dto.response.AnalyticsResponse;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.security.CustomUserDetails;
import com.subpulse.service.SubscriptionService;
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
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subscriptions", description = "Manage subscriptions and analytics")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Add a new subscription")
    public ResponseEntity<SubscriptionResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SubscriptionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.create(userDetails.getId(), request));
    }

    @GetMapping
    @Operation(summary = "Get all subscriptions for the logged-in user")
    public ResponseEntity<List<SubscriptionResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(subscriptionService.getAllByUser(userDetails.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription by ID")
    public ResponseEntity<SubscriptionResponse> getById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        return ResponseEntity.ok(subscriptionService.getById(userDetails.getId(), id));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming renewals within N days (default: 30)")
    public ResponseEntity<List<SubscriptionResponse>> getUpcoming(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {

        return ResponseEntity.ok(subscriptionService.getUpcomingRenewals(userDetails.getId(), days));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a subscription")
    public ResponseEntity<SubscriptionResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequest request) {

        return ResponseEntity.ok(subscriptionService.update(userDetails.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a subscription")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        subscriptionService.delete(userDetails.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle subscription active/inactive status")
    public ResponseEntity<SubscriptionResponse> toggle(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        return ResponseEntity.ok(subscriptionService.toggleActive(userDetails.getId(), id));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Get spend analytics and subscription statistics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(subscriptionService.getAnalytics(userDetails.getId()));
    }
}
