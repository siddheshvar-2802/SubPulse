package com.subpulse.controller;

import com.subpulse.dto.request.SubscriptionRequest;
import com.subpulse.dto.response.AnalyticsResponse;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.notification.NotificationService;
import com.subpulse.repository.SubscriptionRepository;
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

import com.subpulse.dto.event.RenewalAlertEvent;
import com.subpulse.kafka.AlertEventProducer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subscriptions", description = "Manage subscriptions and analytics")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AlertEventProducer alertEventProducer;

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
    @Operation(summary = "Get spend analytics and subscription statistics (normalized to requested or preferred currency)")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String currency) {

        return ResponseEntity.ok(subscriptionService.getAnalytics(userDetails.getId(), currency));
    }

    @PostMapping("/{id}/test-alert-event")
    @Operation(summary = "Manually trigger a Kafka renewal alert event for testing")
    public ResponseEntity<String> triggerTestAlertEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int daysRemaining) {

        SubscriptionResponse sub = subscriptionService.getById(userDetails.getId(), id);
        RenewalAlertEvent event = RenewalAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .subscriptionId(sub.getId())
                .userId(userDetails.getId())
                .userFullName(userDetails.getUsername())
                .userEmail(userDetails.getUsername())
                .serviceName(sub.getServiceName())
                .amount(sub.getAmount())
                .currency(sub.getCurrency())
                .nextBillingDate(sub.getNextBillingDate())
                .daysRemaining(daysRemaining)
                .timestamp(LocalDateTime.now())
                .build();

        alertEventProducer.publishAlertEvent(event);

        return ResponseEntity.ok("Renewal Alert dispatched for '" + sub.getServiceName() + "' via configured channels!");
    }
}
