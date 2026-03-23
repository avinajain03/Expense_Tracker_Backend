package com.expensetracker.ingestion.service;

import com.expensetracker.common.util.EncryptionUtil;
import com.expensetracker.ingestion.dto.*;
import com.expensetracker.ingestion.model.IngestionSource;
import com.expensetracker.ingestion.model.ParsingStatus;
import com.expensetracker.ingestion.model.RawIngestionLog;
import com.expensetracker.ingestion.parser.EmailSenderPatterns;
import com.expensetracker.ingestion.repository.IngestionLogRepository;
import com.expensetracker.transaction.model.*;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.model.User;
import com.expensetracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FromTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Service for email-based transaction ingestion.
 * Supports Gmail OAuth 2.0 (via Gmail API) and generic IMAP connections.
 * Scans inbox for bank/UPI transaction alert emails, extracts data using
 * regex + AI fallback, and creates Transaction documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailParserService {

    private final UserRepository userRepository;
    private final IngestionLogRepository ingestionLogRepository;
    private final TransactionRepository transactionRepository;
    private final DeduplicationService deduplicationService;
    private final TransactionExtractor transactionExtractor;
    private final EmailSenderPatterns emailSenderPatterns;
    private final EncryptionUtil encryptionUtil;

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Value("${app.google.client-secret:}")
    private String googleClientSecret;

    @Value("${app.google.redirect-uri:http://localhost:4200/auth/google/callback}")
    private String googleRedirectUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Gmail OAuth Connection ────────────────────────────────────────

    /**
     * Exchanges a Google OAuth authorization code for tokens and stores
     * the encrypted refresh token in the user document.
     */
    public void connectGmail(String userId, String authCode) {
        if (googleClientId.isBlank() || googleClientSecret.isBlank()) {
            throw new IllegalStateException(
                    "Google OAuth is not configured. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables.");
        }

        // Exchange auth code for tokens via Google's token endpoint
        Map<String, String> tokenRequest = Map.of(
                "code", authCode,
                "client_id", googleClientId,
                "client_secret", googleClientSecret,
                "redirect_uri", googleRedirectUri,
                "grant_type", "authorization_code"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Build form-encoded body
        StringBuilder formBody = new StringBuilder();
        tokenRequest.forEach((k, v) -> {
            if (!formBody.isEmpty()) formBody.append("&");
            formBody.append(k).append("=").append(v);
        });

        HttpEntity<String> entity = new HttpEntity<>(formBody.toString(), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token", entity, String.class);
        } catch (Exception e) {
            log.error("Failed to exchange Google auth code: {}", e.getMessage());
            throw new RuntimeException("Failed to connect Gmail: " + e.getMessage());
        }

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Google token exchange failed with status: " + response.getStatusCode());
        }

        try {
            JsonNode tokenNode = objectMapper.readTree(response.getBody());
            String accessToken = tokenNode.path("access_token").asText();
            String refreshToken = tokenNode.path("refresh_token").asText(null);

            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("No refresh token received — user may need to re-authorize with access_type=offline");
            }

            // Get user's Gmail address using the access token
            String gmailAddress = fetchGmailAddress(accessToken);

            // Store encrypted refresh token in user document
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            User.EmailConnection conn = User.EmailConnection.builder()
                    .provider("GMAIL")
                    .email(gmailAddress)
                    .encryptedRefreshToken(refreshToken != null ? encryptionUtil.encrypt(refreshToken) : null)
                    .lastSyncTime(null)
                    .totalImported(0)
                    .build();

            user.setEmailConnection(conn);
            userRepository.save(user);

            log.info("Gmail connected for user {} ({})", userId, gmailAddress);
        } catch (Exception e) {
            log.error("Failed to process Google token response: {}", e.getMessage());
            throw new RuntimeException("Failed to process Gmail connection: " + e.getMessage());
        }
    }

    // ── IMAP Connection ───────────────────────────────────────────────

    /**
     * Validates IMAP credentials and stores them in the user document.
     */
    public void connectImap(String userId, ImapConfig config) {
        // Validate IMAP connection by attempting to connect
        Properties props = buildImapProperties(config);
        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(config.getHost(), config.getPort(), config.getEmail(), config.getPassword());
            store.close();
        } catch (MessagingException e) {
            log.error("IMAP connection test failed for {}: {}", config.getEmail(), e.getMessage());
            throw new RuntimeException("IMAP connection failed: " + e.getMessage());
        }

        // Store credentials
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        User.EmailConnection conn = User.EmailConnection.builder()
                .provider("IMAP")
                .email(config.getEmail())
                .imapHost(config.getHost())
                .imapPort(config.getPort())
                .encryptedImapPassword(encryptionUtil.encrypt(config.getPassword()))
                .useSsl(config.isUseSsl())
                .lastSyncTime(null)
                .totalImported(0)
                .build();

        user.setEmailConnection(conn);
        userRepository.save(user);

        log.info("IMAP connected for user {} ({})", userId, config.getEmail());
    }

    // ── Email Sync ────────────────────────────────────────────────────

    /**
     * Syncs emails from the connected provider, parses transaction data,
     * and creates Transaction/IngestionLog entries.
     */
    public EmailSyncResponse syncEmails(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        User.EmailConnection conn = user.getEmailConnection();
        if (conn == null) {
            throw new IllegalStateException("No email connection configured. Connect Gmail or IMAP first.");
        }

        List<EmailContent> emails;
        if ("GMAIL".equals(conn.getProvider())) {
            emails = fetchGmailEmails(conn);
        } else if ("IMAP".equals(conn.getProvider())) {
            emails = fetchImapEmails(conn);
        } else {
            throw new IllegalStateException("Unknown email provider: " + conn.getProvider());
        }

        // Process each email
        int transactionsFound = 0;
        int duplicatesSkipped = 0;
        int failedCount = 0;
        List<ParsedTransactionDTO> results = new ArrayList<>();

        for (EmailContent email : emails) {
            ParsedTransactionDTO result = processEmail(userId, email);
            results.add(result);

            switch (result.getStatus()) {
                case "SUCCESS"   -> transactionsFound++;
                case "DUPLICATE" -> duplicatesSkipped++;
                case "FAILED"    -> failedCount++;
            }
        }

        // Update user's sync metadata
        conn.setLastSyncTime(Instant.now());
        conn.setTotalImported(conn.getTotalImported() + transactionsFound);
        userRepository.save(user);

        return EmailSyncResponse.builder()
                .emailsScanned(emails.size())
                .transactionsFound(transactionsFound)
                .duplicatesSkipped(duplicatesSkipped)
                .failedCount(failedCount)
                .transactions(results)
                .build();
    }

    // ── Connection Status ─────────────────────────────────────────────

    /**
     * Returns the current email connection status for the user.
     */
    public EmailSyncStatus getConnectionStatus(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        User.EmailConnection conn = user.getEmailConnection();
        if (conn == null) {
            return EmailSyncStatus.builder()
                    .connected(false)
                    .syncInProgress(false)
                    .build();
        }

        return EmailSyncStatus.builder()
                .connected(true)
                .provider(conn.getProvider())
                .email(conn.getEmail())
                .lastSyncTime(conn.getLastSyncTime())
                .totalImported(conn.getTotalImported())
                .syncInProgress(false)
                .build();
    }

    // ── Private Helpers ───────────────────────────────────────────────

    /**
     * Simple record to hold email content for processing.
     */
    private record EmailContent(String senderEmail, String subject, String body) {}

    /**
     * Fetches the Gmail user's email address using an access token.
     */
    private String fetchGmailAddress(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://www.googleapis.com/gmail/v1/users/me/profile",
                    HttpMethod.GET, entity, String.class);

            JsonNode profile = objectMapper.readTree(response.getBody());
            return profile.path("emailAddress").asText("unknown@gmail.com");
        } catch (Exception e) {
            log.warn("Could not fetch Gmail address: {}", e.getMessage());
            return "unknown@gmail.com";
        }
    }

    /**
     * Refreshes the Gmail access token using the stored refresh token.
     */
    private String refreshGmailAccessToken(String encryptedRefreshToken) {
        String refreshToken = encryptionUtil.decrypt(encryptedRefreshToken);

        Map<String, String> body = Map.of(
                "client_id", googleClientId,
                "client_secret", googleClientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        StringBuilder formBody = new StringBuilder();
        body.forEach((k, v) -> {
            if (!formBody.isEmpty()) formBody.append("&");
            formBody.append(k).append("=").append(v);
        });

        HttpEntity<String> entity = new HttpEntity<>(formBody.toString(), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token", entity, String.class);

        try {
            JsonNode tokenNode = objectMapper.readTree(response.getBody());
            return tokenNode.path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh Gmail access token: " + e.getMessage());
        }
    }

    /**
     * Fetches bank/UPI emails from Gmail using the Gmail API REST endpoint.
     * Searches for emails from known bank sender addresses.
     */
    private List<EmailContent> fetchGmailEmails(User.EmailConnection conn) {
        if (conn.getEncryptedRefreshToken() == null) {
            log.warn("No refresh token stored — cannot fetch Gmail emails");
            return List.of();
        }

        String accessToken = refreshGmailAccessToken(conn.getEncryptedRefreshToken());

        // Build Gmail search query: from known senders
        List<String> senders = emailSenderPatterns.getAllKnownSenders();
        String query = senders.stream()
                .map(s -> "from:" + s)
                .reduce((a, b) -> a + " OR " + b)
                .orElse("");

        // Only fetch emails from the last 30 days or since last sync
        if (conn.getLastSyncTime() != null) {
            long epochSeconds = conn.getLastSyncTime().getEpochSecond();
            query += " after:" + epochSeconds;
        } else {
            query += " newer_than:30d";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        List<EmailContent> emails = new ArrayList<>();

        try {
            // List message IDs matching the query
            String listUrl = "https://www.googleapis.com/gmail/v1/users/me/messages?q="
                    + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                    + "&maxResults=50";

            ResponseEntity<String> listResponse = restTemplate.exchange(
                    listUrl, HttpMethod.GET, entity, String.class);

            JsonNode listNode = objectMapper.readTree(listResponse.getBody());
            JsonNode messages = listNode.path("messages");

            if (messages.isMissingNode() || !messages.isArray()) {
                log.info("No matching Gmail messages found");
                return emails;
            }

            // Fetch each message's content
            for (JsonNode msg : messages) {
                String messageId = msg.path("id").asText();
                try {
                    EmailContent content = fetchGmailMessage(accessToken, messageId);
                    if (content != null) {
                        emails.add(content);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch Gmail message {}: {}", messageId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to list Gmail messages: {}", e.getMessage());
        }

        log.info("Fetched {} bank/UPI emails from Gmail", emails.size());
        return emails;
    }

    /**
     * Fetches a single Gmail message by ID and extracts subject + body.
     */
    private EmailContent fetchGmailMessage(String accessToken, String messageId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://www.googleapis.com/gmail/v1/users/me/messages/" + messageId + "?format=full";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        JsonNode message = objectMapper.readTree(response.getBody());

        // Extract sender and subject from headers
        String sender = "";
        String subject = "";
        JsonNode headersNode = message.path("payload").path("headers");
        if (headersNode.isArray()) {
            for (JsonNode h : headersNode) {
                String name = h.path("name").asText().toLowerCase();
                if ("from".equals(name)) sender = h.path("value").asText();
                if ("subject".equals(name)) subject = h.path("value").asText();
            }
        }

        // Extract body (try text/plain first, then text/html)
        String body = extractGmailBody(message.path("payload"));
        if (body.isBlank()) {
            return null;
        }

        return new EmailContent(sender, subject, emailSenderPatterns.stripHtml(body));
    }

    /**
     * Recursively extracts body text from Gmail message payload.
     */
    private String extractGmailBody(JsonNode payload) {
        // Check if this part has data directly
        String data = payload.path("body").path("data").asText(null);
        if (data != null && !data.isBlank()) {
            return new String(Base64.getUrlDecoder().decode(data));
        }

        // Check parts recursively
        JsonNode parts = payload.path("parts");
        if (parts.isArray()) {
            // Prefer text/plain
            for (JsonNode part : parts) {
                if ("text/plain".equals(part.path("mimeType").asText())) {
                    String partData = part.path("body").path("data").asText(null);
                    if (partData != null) {
                        return new String(Base64.getUrlDecoder().decode(partData));
                    }
                }
            }
            // Fall back to text/html
            for (JsonNode part : parts) {
                if ("text/html".equals(part.path("mimeType").asText())) {
                    String partData = part.path("body").path("data").asText(null);
                    if (partData != null) {
                        return new String(Base64.getUrlDecoder().decode(partData));
                    }
                }
            }
            // Recurse into nested parts
            for (JsonNode part : parts) {
                String result = extractGmailBody(part);
                if (!result.isBlank()) return result;
            }
        }

        return "";
    }

    /**
     * Fetches bank/UPI emails from an IMAP mailbox.
     */
    private List<EmailContent> fetchImapEmails(User.EmailConnection conn) {
        Properties props = new Properties();
        props.put("mail.store.protocol", conn.isUseSsl() ? "imaps" : "imap");
        props.put("mail.imaps.host", conn.getImapHost());
        props.put("mail.imaps.port", String.valueOf(conn.getImapPort()));

        List<EmailContent> emails = new ArrayList<>();

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore(conn.isUseSsl() ? "imaps" : "imap");
            String password = encryptionUtil.decrypt(conn.getEncryptedImapPassword());
            store.connect(conn.getImapHost(), conn.getImapPort(), conn.getEmail(), password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // Build search term for known senders
            List<String> senders = emailSenderPatterns.getAllKnownSenders();
            SearchTerm[] senderTerms = senders.stream()
                    .map(s -> {
                        try {
                            return new FromTerm(new InternetAddress(s));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(SearchTerm[]::new);

            if (senderTerms.length == 0) {
                inbox.close(false);
                store.close();
                return emails;
            }

            SearchTerm combinedTerm = senderTerms.length == 1
                    ? senderTerms[0]
                    : new OrTerm(senderTerms);

            Message[] messages = inbox.search(combinedTerm);
            int limit = Math.min(messages.length, 50); // limit to 50 emails per sync

            for (int i = 0; i < limit; i++) {
                try {
                    Message msg = messages[messages.length - 1 - i]; // newest first
                    String sender = msg.getFrom() != null && msg.getFrom().length > 0
                            ? msg.getFrom()[0].toString() : "";
                    String subject = msg.getSubject() != null ? msg.getSubject() : "";
                    String body = extractImapBody(msg);

                    if (!body.isBlank()) {
                        emails.add(new EmailContent(sender, subject, emailSenderPatterns.stripHtml(body)));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process IMAP message: {}", e.getMessage());
                }
            }

            inbox.close(false);
            store.close();
        } catch (MessagingException e) {
            log.error("IMAP sync failed: {}", e.getMessage());
            throw new RuntimeException("IMAP sync failed: " + e.getMessage());
        }

        log.info("Fetched {} bank/UPI emails via IMAP", emails.size());
        return emails;
    }

    /**
     * Extracts text body from an IMAP message.
     */
    private String extractImapBody(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    return (String) part.getContent();
                }
                if (part.isMimeType("text/html")) {
                    sb.append((String) part.getContent());
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Processes a single email: extracts transaction data, checks duplicates,
     * and creates Transaction + IngestionLog entries.
     */
    private ParsedTransactionDTO processEmail(String userId, EmailContent email) {
        String combinedText = email.subject() + " " + email.body();

        // Check raw content duplicate
        if (deduplicationService.isDuplicateRaw(userId, combinedText)) {
            logIngestion(userId, combinedText, null, ParsingStatus.DUPLICATE, null, null);
            return ParsedTransactionDTO.builder()
                    .rawText(combinedText.substring(0, Math.min(200, combinedText.length())))
                    .status("DUPLICATE")
                    .build();
        }

        // Try regex extraction from email content
        Optional<Double> amount = emailSenderPatterns.extractAmount(combinedText);
        Optional<String> merchant = emailSenderPatterns.extractMerchant(combinedText);
        Optional<String> refNumber = emailSenderPatterns.extractRefNumber(combinedText);
        Optional<String> upiPlatform = emailSenderPatterns.detectUpiPlatform(email.senderEmail(), combinedText);
        Optional<String> bankName = emailSenderPatterns.detectBankFromSender(email.senderEmail());
        String transactionType = emailSenderPatterns.detectTransactionType(combinedText);

        ParsedTransactionDTO dto;

        if (amount.isPresent()) {
            // Regex extraction succeeded
            dto = ParsedTransactionDTO.builder()
                    .amount(amount.get())
                    .merchant(merchant.orElse(null))
                    .refNumber(refNumber.orElse(null))
                    .upiPlatform(upiPlatform.orElse(null))
                    .bankName(bankName.orElse(null))
                    .transactionType(transactionType)
                    .paymentMode(upiPlatform.isPresent() ? "UPI" : null)
                    .confidence(merchant.isPresent() ? 0.85 : 0.7)
                    .rawText(combinedText.substring(0, Math.min(200, combinedText.length())))
                    .build();
        } else {
            // Try AI fallback
            Optional<ParsedTransactionDTO> aiResult = transactionExtractor.extract(combinedText);
            if (aiResult.isEmpty()) {
                logIngestion(userId, combinedText, null, ParsingStatus.FAILED, null,
                        "Could not extract transaction from email");
                return ParsedTransactionDTO.builder()
                        .rawText(combinedText.substring(0, Math.min(200, combinedText.length())))
                        .status("FAILED")
                        .confidence(0.0)
                        .build();
            }
            dto = aiResult.get();
        }

        // Check transaction-level duplicate
        if (deduplicationService.isDuplicateTransaction(userId, dto.getAmount(), dto.getMerchant(), dto.getDate())) {
            logIngestion(userId, combinedText, buildParsedFields(dto), ParsingStatus.DUPLICATE,
                    dto.getConfidence(), null);
            dto.setStatus("DUPLICATE");
            return dto;
        }

        // Create Transaction
        Transaction txn = Transaction.builder()
                .userId(userId)
                .type(mapTransactionType(dto.getTransactionType()))
                .amount(dto.getAmount())
                .merchant(dto.getMerchant())
                .date(dto.getDate() != null ? dto.getDate() : Instant.now())
                .upiPlatform(mapUpiPlatform(dto.getUpiPlatform()))
                .paymentMode(mapPaymentMode(dto.getPaymentMode()))
                .source(IngestionSource.EMAIL)
                .parsingConfidence(dto.getConfidence())
                .status(TransactionStatus.AUTO_PARSED)
                .notes("Auto-parsed from email")
                .build();

        transactionRepository.save(txn);
        logIngestion(userId, combinedText, buildParsedFields(dto), ParsingStatus.SUCCESS,
                dto.getConfidence(), null);

        dto.setStatus("SUCCESS");
        return dto;
    }

    // ── Shared helpers (same pattern as SmsParserService) ─────────────

    private void logIngestion(String userId, String rawContent,
                              RawIngestionLog.ParsedFields parsedFields,
                              ParsingStatus status, Double confidence,
                              String errorMessage) {
        RawIngestionLog logEntry = RawIngestionLog.builder()
                .userId(userId)
                .source(IngestionSource.EMAIL)
                .rawContent(rawContent)
                .parsedFields(parsedFields)
                .parsingStatus(status)
                .parsingConfidence(confidence)
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

    private Properties buildImapProperties(ImapConfig config) {
        Properties props = new Properties();
        props.put("mail.store.protocol", config.isUseSsl() ? "imaps" : "imap");
        props.put("mail.imaps.host", config.getHost());
        props.put("mail.imaps.port", String.valueOf(config.getPort()));
        return props;
    }
}
