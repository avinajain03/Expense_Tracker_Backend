package com.expensetracker.ingestion.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "raw_ingestion_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawIngestionLog {

    @Id
    private String id;

    private String userId;

    private IngestionSource source;

    private String rawContent;

    private ParsedFields parsedFields;

    private String transactionId;

    private ParsingStatus parsingStatus;

    private Double parsingConfidence;

    private String fileName;

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    // Embedded parsed fields extracted from raw content
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedFields {
        private Double amount;
        private String merchant;
        private String upiPlatform;
        private Instant date;
        private String refNumber;
        private String bankName;
    }
}
