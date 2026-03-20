package com.expensetracker.ingestion.service;

import com.expensetracker.ingestion.dto.IngestionResponse;
import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import com.expensetracker.ingestion.model.IngestionSource;
import com.expensetracker.ingestion.model.ParsingStatus;
import com.expensetracker.ingestion.model.RawIngestionLog;
import com.expensetracker.ingestion.parser.SmsRegexPatterns;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.transaction.model.*;
import com.expensetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses individual or bulk SMS messages using regex patterns, logs raw
 * content to the ingestion audit log, creates Transaction documents for
 * successfully parsed messages, and detects duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsParserService {

    private final SmsRegexPatterns smsRegexPatterns;
    private final DeduplicationService deduplicationService;
    private final IngestionLogRepository ingestionLogRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Parse a single SMS text: regex → log → create transaction.
     */
    public ParsedTransactionDTO parseSms(String userId, String smsText) {
        String trimmed = smsText.trim();

        // 1. Check raw-content duplicate
        if (deduplicationService.isDuplicateRaw(userId, trimmed)) {
            logIngestion(userId, trimmed, null, ParsingStatus.DUPLICATE, null, null);
            return ParsedTransactionDTO.builder()
                    .rawText(trimmed)
                    .status("DUPLICATE")
                    .build();
        }

        // 2. Attempt regex parsing
        Optional<ParsedTransactionDTO> parsed = smsRegexPatterns.parse(trimmed);

        if (parsed.isEmpty() || parsed.get().getConfidence() < 0.7) {
            // No match or low confidence → mark as FAILED (AI fallback deferred)
            log.debug("SMS parse failed or low confidence for user {}: {}", userId, trimmed);
            logIngestion(userId, trimmed, null, ParsingStatus.FAILED, null,
                    parsed.isEmpty() ? "No regex pattern matched" : "Confidence below threshold");
            return ParsedTransactionDTO.builder()
                    .rawText(trimmed)
                    .status("FAILED")
                    .confidence(parsed.map(ParsedTransactionDTO::getConfidence).orElse(0.0))
                    .build();
        }

        ParsedTransactionDTO dto = parsed.get();

        // 3. Check transaction-level duplicate
        if (deduplicationService.isDuplicateTransaction(userId, dto.getAmount(), dto.getMerchant(), dto.getDate())) {
            logIngestion(userId, trimmed, buildParsedFields(dto), ParsingStatus.DUPLICATE, dto.getConfidence(), null);
            dto.setStatus("DUPLICATE");
            return dto;
        }

        // 4. Create Transaction document
        Transaction txn = Transaction.builder()
                .userId(userId)
                .type(mapTransactionType(dto.getTransactionType()))
                .amount(dto.getAmount())
                .merchant(dto.getMerchant())
                .date(dto.getDate() != null ? dto.getDate() : Instant.now())
                .upiPlatform(mapUpiPlatform(dto.getUpiPlatform()))
                .paymentMode(mapPaymentMode(dto.getPaymentMode()))
                .source(IngestionSource.SMS)
                .parsingConfidence(dto.getConfidence())
                .status(TransactionStatus.AUTO_PARSED)
                .notes("Auto-parsed from SMS")
                .build();

        Transaction saved = transactionRepository.save(txn);

        // 5. Log ingestion with reference to created transaction
        logIngestion(userId, trimmed, buildParsedFields(dto), ParsingStatus.SUCCESS, dto.getConfidence(), null);

        dto.setStatus("SUCCESS");
        return dto;
    }

    /**
     * Parse a batch of SMS texts and return an aggregated response.
     */
    public IngestionResponse parseBulkSms(String userId, List<String> smsTexts) {
        List<ParsedTransactionDTO> results = new ArrayList<>();
        int parsedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (String sms : smsTexts) {
            ParsedTransactionDTO result = parseSms(userId, sms);
            results.add(result);

            switch (result.getStatus()) {
                case "SUCCESS"   -> parsedCount++;
                case "DUPLICATE" -> duplicateCount++;
                case "FAILED"    -> failedCount++;
            }
        }

        return IngestionResponse.builder()
                .totalCount(smsTexts.size())
                .parsedCount(parsedCount)
                .duplicateCount(duplicateCount)
                .failedCount(failedCount)
                .transactions(results)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────

    private void logIngestion(String userId, String rawContent,
                              RawIngestionLog.ParsedFields parsedFields,
                              ParsingStatus status, Double confidence,
                              String errorMessage) {
        RawIngestionLog logEntry = RawIngestionLog.builder()
                .userId(userId)
                .source(IngestionSource.SMS)
                .rawContent(rawContent)
                .parsedFields(parsedFields)
                .parsingStatus(status)
                .parsingConfidence(confidence)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
        ingestionLogRepository.save(logEntry);
    }

    private RawIngestionLog.ParsedFields buildParsedFields(ParsedTransactionDTO dto) {
        return RawIngestionLog.ParsedFields.builder()
                .amount(dto.getAmount())
                .merchant(dto.getMerchant())
                .upiPlatform(dto.getUpiPlatform())
                .date(dto.getDate())
                .refNumber(dto.getRefNumber())
                .bankName(dto.getBankName())
                .build();
    }

    private TransactionType mapTransactionType(String type) {
        if (type == null) return TransactionType.EXPENSE;
        return switch (type.toUpperCase()) {
            case "INCOME"   -> TransactionType.INCOME;
            case "TRANSFER" -> TransactionType.TRANSFER;
            default         -> TransactionType.EXPENSE;
        };
    }

    private UpiPlatform mapUpiPlatform(String platform) {
        if (platform == null) return null;
        return switch (platform.toUpperCase()) {
            case "GPAY"    -> UpiPlatform.GPAY;
            case "PHONEPE" -> UpiPlatform.PHONEPE;
            case "PAYTM"   -> UpiPlatform.PAYTM;
            case "CRED"    -> UpiPlatform.CRED;
            default        -> UpiPlatform.OTHER;
        };
    }

    private PaymentMode mapPaymentMode(String mode) {
        if (mode == null) return null;
        return switch (mode.toUpperCase()) {
            case "UPI"         -> PaymentMode.UPI;
            case "CASH"        -> PaymentMode.CASH;
            case "CARD"        -> PaymentMode.CARD;
            case "NET_BANKING" -> PaymentMode.NET_BANKING;
            default            -> PaymentMode.UPI;
        };
    }
}
