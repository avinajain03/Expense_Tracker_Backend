package com.expensetracker.transaction.model;

import com.expensetracker.ingestion.model.IngestionSource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    private String id;

    private String userId;

    private TransactionType type;

    private Double amount;

    @Builder.Default
    private String currency = "INR";

    private String category;

    private String subCategory;

    private UpiPlatform upiPlatform;

    private PaymentMode paymentMode;

    private String merchant;

    private String notes;

    private List<String> tags;

    private IngestionSource source;

    private String sourceRef; // reference to raw_ingestion_log id

    private Double parsingConfidence;

    @Builder.Default
    private TransactionStatus status = TransactionStatus.AUTO_PARSED;

    @Builder.Default
    private Boolean isRecurring = false;

    private String recurringId;

    private Instant date;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
