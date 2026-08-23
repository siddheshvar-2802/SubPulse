package com.subpulse.controller;

import com.subpulse.security.CustomUserDetails;
import com.subpulse.service.MonthlyDigestReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports & Digests", description = "Generate and export monthly PDF reports and email digests")
public class ReportController {

    private final MonthlyDigestReportService monthlyDigestReportService;

    @GetMapping(value = "/monthly-digest/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download on-demand Executive Monthly Digest PDF")
    public ResponseEntity<byte[]> downloadMonthlyPdf(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String currency) {

        byte[] pdfBytes = monthlyDigestReportService.generatePdfReport(userDetails.getId(), currency);
        String fileName = "SubPulse_Monthly_Digest_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @PostMapping("/monthly-digest/send-email")
    @Operation(summary = "Trigger on-demand dispatch of Monthly Digest Email with PDF attachment")
    public ResponseEntity<String> sendMonthlyDigestEmail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String currency) {

        monthlyDigestReportService.sendMonthlyDigestEmail(userDetails.getId(), currency);
        return ResponseEntity.ok("Monthly executive digest email with attached PDF has been dispatched to " + userDetails.getUsername());
    }
}
