package com.expensetracker.user.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String fullName;

    private Integer age;

    private Double monthlyIncome;

    @Builder.Default
    private String currency = "INR";

    private String avatarUrl;

    @Builder.Default
    private Preferences preferences = new Preferences();

    private EmailConnection emailConnection;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Embedded preferences record
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Preferences {
        private String theme = "dark";
        private String defaultUpiApp = "gpay";
        private boolean notificationsEnabled = true;
    }

    // Embedded email connection for Gmail OAuth / IMAP
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmailConnection {
        private String provider;           // GMAIL or IMAP
        private String email;              // connected email address
        private String encryptedRefreshToken; // AES-encrypted refresh token
        private String imapHost;           // IMAP host (for IMAP provider)
        private Integer imapPort;          // IMAP port
        private String encryptedImapPassword; // AES-encrypted IMAP password
        private boolean useSsl;
        private Instant lastSyncTime;
        private int totalImported;
    }
}
