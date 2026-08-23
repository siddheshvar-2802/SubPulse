package com.subpulse.controller;

import com.subpulse.dto.response.AiOptimizationResponse;
import com.subpulse.security.CustomUserDetails;
import com.subpulse.service.AiOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI & Smart Cost Optimization Controller.
 */
@RestController
@RequestMapping("/api/v1/subscriptions/ai-optimization")
@RequiredArgsConstructor
@Tag(name = "AI Cost Optimization", description = "AI subscription overlap detection, plan arbitrage, and savings engine")
@SecurityRequirement(name = "Bearer Authentication")
public class AiOptimizationController {

    private final AiOptimizationService aiOptimizationService;

    @GetMapping
    @Operation(summary = "Get AI-driven cost optimization insights, recommendations, and health score")
    public ResponseEntity<AiOptimizationResponse> getOptimizationInsights(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String currency) {

        return ResponseEntity.ok(aiOptimizationService.analyzeSubscriptions(userDetails.getId(), currency));
    }
}
