package com.expensetracker.ingestion.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsIngestionRequest {

    @NotEmpty(message = "SMS texts list must not be empty")
    private List<String> smsTexts;
}
