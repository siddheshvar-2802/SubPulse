package com.subpulse.controller;

import com.subpulse.dto.request.CsvImportConfirmRequest;
import com.subpulse.dto.response.CsvImportPreviewDto;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.security.CustomUserDetails;
import com.subpulse.service.BankStatementParserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions/import")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CSV & Statement Importer", description = "Auto-detect subscriptions from bank statement CSVs and bulk import")
public class ImportController {

    private final BankStatementParserService bankStatementParserService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload bank statement CSV and preview auto-detected recurring subscriptions")
    public ResponseEntity<List<CsvImportPreviewDto>> previewImport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(bankStatementParserService.parseAndPreviewCsv(userDetails.getId(), file));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm and bulk import selected detected subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> confirmImport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CsvImportConfirmRequest request) {

        return ResponseEntity.ok(bankStatementParserService.confirmImport(userDetails.getId(), request));
    }

    @GetMapping(value = "/template", produces = "text/csv")
    @Operation(summary = "Download a sample bank statement CSV template")
    public ResponseEntity<String> getTemplate() {
        String csv = bankStatementParserService.generateSampleTemplateCsv();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "subpulse_sample_statement.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }
}
