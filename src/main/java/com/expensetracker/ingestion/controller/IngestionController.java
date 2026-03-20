package com.expensetracker.ingestion.controller;

import com.expensetracker.common.dto.ApiResponse;
import com.expensetracker.ingestion.dto.IngestionResponse;
import com.expensetracker.ingestion.dto.SmsIngestionRequest;
import com.expensetracker.ingestion.model.RawIngestionLog;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.ingestion.service.SmsParserService;
import com.expensetracker.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Tag(name = "Data Ingestion", description = "SMS, email, and bank statement ingestion endpoints")
public class IngestionController {

    private final SmsParserService smsParserService;
    private final IngestionLogRepository ingestionLogRepository;

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
}
