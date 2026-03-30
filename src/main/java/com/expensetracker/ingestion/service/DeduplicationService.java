package com.expensetracker.ingestion.service;

import com.expensetracker.ingestion.model.ParsingStatus;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Checks for duplicate raw content in the ingestion log and duplicate
 * transactions based on amount + merchant + date proximity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final IngestionLogRepository ingestionLogRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Check if the exact raw content has already been ingested for this user.
     */
    public boolean isDuplicateRaw(String userId, String rawContent) {
        // Only treat as duplicate if a non-FAILED entry exists.
        // FAILED entries (no regex match) should be retried after parser improvements.
        boolean dup = ingestionLogRepository
                .existsByUserIdAndRawContentAndParsingStatusNot(userId, rawContent.trim(), ParsingStatus.FAILED);
        if (dup) {
            log.debug("Duplicate raw content detected for user {}", userId);
        }
        return dup;
    }

    /**
     * Check if a transaction with the same amount, merchant, and date
     * (within ±5 minutes) already exists for this user.
     */
    public boolean isDuplicateTransaction(String userId, Double amount, String merchant, Instant date) {
        if (amount == null || merchant == null || date == null) {
            return false;
        }
        Instant from = date.minus(5, ChronoUnit.MINUTES);
        Instant to = date.plus(5, ChronoUnit.MINUTES);

        return !transactionRepository
                .findByUserIdAndAmountAndMerchantIgnoreCaseAndDateBetween(userId, amount, merchant, from, to)
                .isEmpty();
    }
}
