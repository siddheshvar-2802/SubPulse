package com.subpulse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportConfirmRequest {

    @NotEmpty(message = "At least one subscription item must be selected for import")
    @Valid
    private List<CsvImportItemRequest> subscriptions;
}
