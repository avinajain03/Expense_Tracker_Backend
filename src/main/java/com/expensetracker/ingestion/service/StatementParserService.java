package com.expensetracker.ingestion.service;

import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import com.expensetracker.ingestion.dto.StatementUploadResponse;
import com.expensetracker.ingestion.model.IngestionSource;
import com.expensetracker.ingestion.model.ParsingStatus;
import com.expensetracker.ingestion.model.RawIngestionLog;
import com.expensetracker.ingestion.parser.CsvStatementParser;
import com.expensetracker.ingestion.parser.PdfStatementParser;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.transaction.model.*;
import com.expensetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates bank statement parsing (PDF / CSV / Excel),
 * deduplication, transaction creation, and ingestion logging.
 *
 * Follows the same patterns as {@link SmsParserService} and
 * {@link EmailParserService} for consistency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementParserService {

    private final PdfStatementParser pdfStatementParser;
    private final CsvStatementParser csvStatementParser;
    private final DeduplicationService deduplicationService;
    private final IngestionLogRepository ingestionLogRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Parses an uploaded bank statement file and creates transactions.
     *
     * @param userId   authenticated user's ID
     * @param file     the uploaded multipart file (PDF, CSV, or XLSX)
     * @param bankName the bank name (HDFC, SBI, ICICI, AXIS, KOTAK, OTHER)
     * @return response with parsing summary and transaction details
     */
    public StatementUploadResponse parseStatement(String userId, MultipartFile file, String bankName) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String fileType = detectFileType(file);

        log.info("Parsing {} statement '{}' for user {} (bank: {})", fileType, fileName, userId, bankName);

        // 1. Parse the file using the appropriate parser
        List<ParsedTransactionDTO> parsedRows;
        try {
            parsedRows = switch (fileType) {
                case "PDF"  -> pdfStatementParser.parse(file, bankName);
                case "CSV"  -> csvStatementParser.parseCsv(file, bankName);
                case "XLSX" -> csvStatementParser.parseExcel(file, bankName);
                default     -> {
                    log.error("Unsupported file type: {}", fileType);
                    yield List.of();
                }
            };
        } catch (Exception e) {
            log.error("Failed to parse statement '{}': {}", fileName, e.getMessage());
            return StatementUploadResponse.builder()
                    .fileName(fileName)
                    .bankName(bankName)
                    .fileType(fileType)
                    .totalRows(0)
                    .parsedCount(0)
                    .duplicateCount(0)
                    .failedCount(1)
                    .transactions(List.of())
                    .build();
        }

        if (parsedRows.isEmpty()) {
            log.warn("No transactions extracted from statement '{}'", fileName);
            return StatementUploadResponse.builder()
                    .fileName(fileName)
                    .bankName(bankName)
                    .fileType(fileType)
                    .totalRows(0)
                    .parsedCount(0)
                    .duplicateCount(0)
                    .failedCount(0)
                    .transactions(List.of())
                    .build();
        }

        // 2. Process each parsed row: deduplicate, create transaction, log
        List<ParsedTransactionDTO> results = new ArrayList<>();
        int parsedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (ParsedTransactionDTO dto : parsedRows) {
            ParsedTransactionDTO result = processRow(userId, dto, fileName);
            results.add(result);

            switch (result.getStatus()) {
                case "SUCCESS"   -> parsedCount++;
                case "DUPLICATE" -> duplicateCount++;
                case "FAILED"    -> failedCount++;
            }
        }

        log.info("Statement '{}' processed: {} parsed, {} duplicates, {} failed out of {} rows",
                fileName, parsedCount, duplicateCount, failedCount, parsedRows.size());

        return StatementUploadResponse.builder()
                .fileName(fileName)
                .bankName(bankName)
                .fileType(fileType)
                .totalRows(parsedRows.size())
                .parsedCount(parsedCount)
                .duplicateCount(duplicateCount)
                .failedCount(failedCount)
                .transactions(results)
                .build();
    }

    // ── Private Helpers ──────────────────────────────────────────────

    /**
     * Processes a single parsed row: check duplicates, create Transaction, log ingestion.
     */
    private ParsedTransactionDTO processRow(String userId, ParsedTransactionDTO dto, String fileName) {
        String rawText = dto.getRawText() != null ? dto.getRawText() : "";

        // Validate minimum required fields
        if (dto.getAmount() == null || dto.getAmount() <= 0) {
            logIngestion(userId, rawText, null, ParsingStatus.FAILED, null,
                    "Invalid or missing amount", fileName);
            dto.setStatus("FAILED");
            return dto;
        }

        // Check raw content duplicate (using description as raw content)
        String dedupeKey = String.format("%s|%.2f|%s",
                dto.getMerchant() != null ? dto.getMerchant() : "",
                dto.getAmount(),
                dto.getDate() != null ? dto.getDate().toString() : "");

        // Check transaction-level duplicate
        if (deduplicationService.isDuplicateTransaction(
                userId, dto.getAmount(), dto.getMerchant(), dto.getDate())) {
            logIngestion(userId, rawText, buildParsedFields(dto), ParsingStatus.DUPLICATE,
                    dto.getConfidence(), null, fileName);
            dto.setStatus("DUPLICATE");
            return dto;
        }

        // Create Transaction document
        Transaction txn = Transaction.builder()
                .userId(userId)
                .type(mapTransactionType(dto.getTransactionType()))
                .amount(dto.getAmount())
                .merchant(dto.getMerchant())
                .date(dto.getDate() != null ? dto.getDate() : Instant.now())
                .upiPlatform(mapUpiPlatform(dto.getUpiPlatform()))
                .paymentMode(mapPaymentMode(dto.getPaymentMode()))
                .source(IngestionSource.BANK_STATEMENT)
                .parsingConfidence(dto.getConfidence())
                .status(TransactionStatus.AUTO_PARSED)
                .notes("Auto-parsed from bank statement: " + fileName)
                .build();

        Transaction saved = transactionRepository.save(txn);

        // Log successful ingestion
        logIngestion(userId, rawText, buildParsedFields(dto), ParsingStatus.SUCCESS,
                dto.getConfidence(), null, fileName);

        dto.setStatus("SUCCESS");
        return dto;
    }

    /**
     * Detects the file type from content type and file extension.
     */
    private String detectFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if (contentType != null) {
            if (contentType.equals("application/pdf") || fileName.endsWith(".pdf")) {
                return "PDF";
            }
            if (contentType.equals("text/csv")
                    || contentType.equals("application/csv")
                    || fileName.endsWith(".csv")) {
                return "CSV";
            }
            if (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    || fileName.endsWith(".xlsx")) {
                return "XLSX";
            }
            if (contentType.equals("application/vnd.ms-excel")
                    || fileName.endsWith(".xls")) {
                return "XLSX"; // POI handles both
            }
        }

        // Fallback to extension-based detection
        if (fileName.endsWith(".pdf")) return "PDF";
        if (fileName.endsWith(".csv")) return "CSV";
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) return "XLSX";

        log.warn("Could not determine file type for '{}' (contentType={})", fileName, contentType);
        return "UNKNOWN";
    }

    private void logIngestion(String userId, String rawContent,
                              RawIngestionLog.ParsedFields parsedFields,
                              ParsingStatus status, Double confidence,
                              String errorMessage, String fileName) {
        RawIngestionLog logEntry = RawIngestionLog.builder()
                .userId(userId)
                .source(IngestionSource.BANK_STATEMENT)
                .rawContent(rawContent)
                .parsedFields(parsedFields)
                .parsingStatus(status)
                .parsingConfidence(confidence)
                .fileName(fileName)
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
