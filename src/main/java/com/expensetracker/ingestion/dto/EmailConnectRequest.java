package com.expensetracker.ingestion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailConnectRequest {

    /**
     * Provider type: GMAIL or IMAP.
     */
    @NotBlank(message = "Provider is required (GMAIL or IMAP)")
    private String provider;

    /**
     * OAuth authorization code (required for GMAIL provider).
     */
    private String authCode;

    /**
     * IMAP configuration (required for IMAP provider).
     */
    @Valid
    private ImapConfig imapConfig;
}
