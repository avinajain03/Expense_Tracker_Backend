package com.expensetracker.ingestion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedTransactionDTO {

    private Double amount;
    private String merchant;
    private Instant date;
    private String refNumber;
    private String upiPlatform;
    private String paymentMode;
    private String transactionType;
    private String bankName;
    private Double confidence;
    private String rawText;
    private String status; // SUCCESS, FAILED, DUPLICATE
}
