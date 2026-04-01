package com.expensetracker.ingestion.controller;

import com.expensetracker.common.dto.ApiResponse;
import com.expensetracker.ingestion.dto.*;
import com.expensetracker.ingestion.model.RawIngestionLog;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.ingestion.service.EmailParserService;
import com.expensetracker.ingestion.service.SmsParserService;
import com.expensetracker.ingestion.service.StatementParserService;
import com.expensetracker.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Tag(name = "Data Ingestion", description = "SMS, email, and bank statement ingestion endpoints")
public class IngestionController {

    private final SmsParserService smsParserService;
    private final EmailParserService emailParserService;
    private final StatementParserService statementParserService;
    private final IngestionLogRepository ingestionLogRepository;

    // ── SMS Endpoints ─────────────────────────────────────────────────

    /**
     * Submit one or more SMS messages for parsing.
     */
    @PostMapping("/sms")
    @Operation(summary = "Submit SMS text(s) for transaction parsing")
    public ResponseEntity<ApiResponse<IngestionResponse>> submitSms(
            @Valid @RequestBody SmsIngestionRequest request,
            @AuthenticationPrincipal User user) {

        IngestionResponse response = smsParserService.parseBulkSms(user.getId(), request.getSmsTexts());
        return ResponseEntity.ok(ApiResponse.success("SMS parsing complete", response));
    }

    /**
     * Alias for bulk SMS submission (same behaviour as /sms).
     */
    @PostMapping("/sms/bulk")
    @Operation(summary = "Submit multiple SMS messages in batch")
    public ResponseEntity<ApiResponse<IngestionResponse>> submitBulkSms(
            @Valid @RequestBody SmsIngestionRequest request,
            @AuthenticationPrincipal User user) {

        IngestionResponse response = smsParserService.parseBulkSms(user.getId(), request.getSmsTexts());
        return ResponseEntity.ok(ApiResponse.success("Bulk SMS parsing complete", response));
    }

    // ── Email Endpoints ───────────────────────────────────────────────

    /**
     * Connect a Gmail (OAuth) or IMAP email account for transaction scanning.
     */
    @PostMapping("/email/connect")
    @Operation(summary = "Connect Gmail or IMAP email account")
    public ResponseEntity<ApiResponse<String>> connectEmail(
            @Valid @RequestBody EmailConnectRequest request,
            @AuthenticationPrincipal User user) {

        String provider = request.getProvider().toUpperCase();
        if ("GMAIL".equals(provider)) {
            emailParserService.connectGmail(user.getId(), request.getAuthCode());
        } else if ("IMAP".equals(provider)) {
            if (request.getImapConfig() == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("IMAP configuration is required for IMAP provider"));
            }
            emailParserService.connectImap(user.getId(), request.getImapConfig());
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Unsupported provider: " + provider + ". Use GMAIL or IMAP."));
        }

        return ResponseEntity.ok(ApiResponse.success("Email connected successfully via " + provider, provider));
    }

    /**
     * Trigger an email sync to scan for bank/UPI transaction emails.
     */
    @PostMapping("/email/sync")
    @Operation(summary = "Trigger email scan for transaction emails")
    public ResponseEntity<ApiResponse<EmailSyncResponse>> syncEmail(
            @AuthenticationPrincipal User user) {

        EmailSyncResponse response = emailParserService.syncEmails(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Email sync complete", response));
    }

    /**
     * Get the current email connection status and sync info.
     */
    @GetMapping("/email/status")
    @Operation(summary = "Get email connection status")
    public ResponseEntity<ApiResponse<EmailSyncStatus>> getEmailStatus(
            @AuthenticationPrincipal User user) {

        EmailSyncStatus status = emailParserService.getConnectionStatus(user.getId());
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ── Statement Endpoints ───────────────────────────────────────────

    /**
     * Upload a bank statement file (PDF, CSV, or Excel) for parsing.
     * Extracts transactions from the file and stores them in the database.
     */
    @PostMapping(value = "/statement/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload bank statement (PDF/CSV/Excel) for transaction parsing")
    public ResponseEntity<ApiResponse<StatementUploadResponse>> uploadStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bankName", defaultValue = "OTHER") String bankName,
            @AuthenticationPrincipal User user) {

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty. Please upload a valid bank statement."));
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        if (!isSupportedFileType(fileName)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Unsupported file type. Please upload a PDF, CSV, or Excel (.xlsx) file."));
        }

        StatementUploadResponse response = statementParserService.parseStatement(
                user.getId(), file, bankName.toUpperCase());

        String message = String.format("Statement parsed: %d transactions found, %d duplicates, %d failed",
                response.getParsedCount(), response.getDuplicateCount(), response.getFailedCount());

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    /**
     * Check the parsing status of a specific ingestion log entry (for large files).
     */
    @GetMapping("/statement/{id}/status")
    @Operation(summary = "Check statement parsing status by ingestion log ID")
    public ResponseEntity<ApiResponse<RawIngestionLog>> getStatementStatus(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {

        Optional<RawIngestionLog> logEntry = ingestionLogRepository.findById(id);
        if (logEntry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Verify the log belongs to the authenticated user
        RawIngestionLog entry = logEntry.get();
        if (!entry.getUserId().equals(user.getId())) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied to this ingestion log entry"));
        }

        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    // ── Ingestion Log ─────────────────────────────────────────────────

    /**
     * Retrieve paginated ingestion audit log for the authenticated user.
     */
    @GetMapping("/log")
    @Operation(summary = "View raw ingestion audit log (paginated)")
    public ResponseEntity<ApiResponse<Page<RawIngestionLog>>> getIngestionLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {

        Pageable pageable = PageRequest.of(page, size);
        Page<RawIngestionLog> logs = ingestionLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    // ── Private Helpers ───────────────────────────────────────────────

    private boolean isSupportedFileType(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".csv")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }
}

