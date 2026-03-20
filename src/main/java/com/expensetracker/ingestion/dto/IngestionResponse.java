package com.expensetracker.ingestion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionResponse {

    private int totalCount;
    private int parsedCount;
    private int duplicateCount;
    private int failedCount;
    private List<ParsedTransactionDTO> transactions;
}
