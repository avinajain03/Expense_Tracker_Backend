package com.expensetracker.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImapConfig {

    @NotBlank(message = "IMAP host is required")
    private String host;

    @Positive(message = "Port must be a positive number")
    private int port;

    @NotBlank(message = "Email address is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @Builder.Default
    private boolean useSsl = true;
}
