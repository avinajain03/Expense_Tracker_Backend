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
public class EmailSyncResponse {

    private int emailsScanned;
    private int transactionsFound;
    private int duplicatesSkipped;
    private int failedCount;
    private List<ParsedTransactionDTO> transactions;
}
