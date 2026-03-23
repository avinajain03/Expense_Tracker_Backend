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
public class EmailSyncStatus {

    private boolean connected;
    private String provider;
    private String email;
    private Instant lastSyncTime;
    private int totalImported;
    private boolean syncInProgress;
}
