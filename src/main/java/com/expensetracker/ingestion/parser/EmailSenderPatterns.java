package com.expensetracker.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Known bank & UPI sender email addresses and regex patterns
 * for extracting transaction data from email subjects and bodies.
 */
@Component
@Slf4j
public class EmailSenderPatterns {

    // ── Known bank sender email addresses ─────────────────────────────

    public static final List<String> BANK_SENDER_EMAILS = List.of(
            "alerts@hdfcbank.net",
            "transaction@icicibank.com",
            "noreply@sbi.co.in",
            "donotreply@axisbank.com",
            "alerts@kotak.com",
            "alerts@yesbank.in",
            "alerts@pnb.co.in",
            "alerts@bobfinancial.com",
            "noreply@indusind.com",
            "alerts@canarabank.in"
    );

    // ── Known UPI/payment sender email addresses ──────────────────────

    public static final List<String> UPI_SENDER_EMAILS = List.of(
            "noreply@google.com",         // Google Pay receipts
            "noreply@phonepe.com",
            "no-reply@paytm.com",
            "alerts@amazonpay.in",
            "noreply@cred.club"
    );

    /**
     * Returns all known sender email addresses (bank + UPI) for email scanning.
     */
    public List<String> getAllKnownSenders() {
        return List.of(
                BANK_SENDER_EMAILS.stream().toArray(String[]::new),
                UPI_SENDER_EMAILS.stream().toArray(String[]::new)
        ).stream()
                .flatMap(arr -> java.util.Arrays.stream((String[]) arr))
                .toList();
    }

    // ── Regex patterns for email transaction extraction ────────────────

    /**
     * Amount pattern: matches "Rs. 1,234.56" or "INR 1234.56" or "₹1,234.56".
     */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Date pattern: matches common date formats in emails.
     */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Reference number pattern: matches UPI ref, transaction IDs, etc.
     */
    private static final Pattern REF_PATTERN = Pattern.compile(
            "(?:Ref\\.?\\s*(?:No\\.?)?|Transaction\\s*(?:ID|No\\.?)|UPI\\s*Ref)\\s*[:#]?\\s*([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Merchant/payee pattern: matches common "to MERCHANT" or "at MERCHANT" phrases.
     */
    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
            "(?:to|at|towards|paid to|transferred to|merchant)\\s+([A-Za-z0-9][A-Za-z0-9 &.'_-]{1,50}?)(?:\\s+(?:on|via|ref|using|for|Rs|INR)|\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    // ── UPI platform detection from email content ────────────────────

    private static final Map<String, String> UPI_KEYWORDS = Map.of(
            "google pay", "GPAY",
            "gpay", "GPAY",
            "phonepe", "PHONEPE",
            "paytm", "PAYTM",
            "cred", "CRED",
            "amazonpay", "OTHER",
            "amazon pay", "OTHER"
    );

    /**
     * Detects UPI platform from email sender or body content.
     */
    public Optional<String> detectUpiPlatform(String senderEmail, String content) {
        String combined = (senderEmail + " " + content).toLowerCase();
        for (Map.Entry<String, String> entry : UPI_KEYWORDS.entrySet()) {
            if (combined.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts amount from email text content.
     */
    public Optional<Double> extractAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Optional.of(Double.parseDouble(m.group(1).replace(",", "")));
            } catch (NumberFormatException e) {
                log.debug("Could not parse amount: {}", m.group(1));
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts merchant/payee from email text content.
     */
    public Optional<String> extractMerchant(String text) {
        Matcher m = MERCHANT_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1).trim());
        }
        return Optional.empty();
    }

    /**
     * Extracts date string from email text content.
     */
    public Optional<String> extractDateString(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
    }

    /**
     * Extracts reference number from email text content.
     */
    public Optional<String> extractRefNumber(String text) {
        Matcher m = REF_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
    }

    /**
     * Detects transaction type from email content: EXPENSE, INCOME, or TRANSFER.
     */
    public String detectTransactionType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("credited") || lower.contains("received") || lower.contains("credit")) {
            return "INCOME";
        }
        if (lower.contains("transferred") || lower.contains("transfer")) {
            return "TRANSFER";
        }
        return "EXPENSE";
    }

    /**
     * Detects bank name from sender email address.
     */
    public Optional<String> detectBankFromSender(String senderEmail) {
        if (senderEmail == null) return Optional.empty();
        String lower = senderEmail.toLowerCase();
        if (lower.contains("hdfc")) return Optional.of("HDFC");
        if (lower.contains("icici")) return Optional.of("ICICI");
        if (lower.contains("sbi")) return Optional.of("SBI");
        if (lower.contains("axis")) return Optional.of("Axis");
        if (lower.contains("kotak")) return Optional.of("Kotak");
        if (lower.contains("yes")) return Optional.of("Yes Bank");
        if (lower.contains("pnb")) return Optional.of("PNB");
        if (lower.contains("indusind")) return Optional.of("IndusInd");
        if (lower.contains("canara")) return Optional.of("Canara");
        return Optional.empty();
    }

    /**
     * Strips HTML tags from email body content, returning plain text.
     */
    public String stripHtml(String html) {
        if (html == null) return "";
        // Remove script/style blocks entirely
        String cleaned = html.replaceAll("(?si)<(script|style).*?</\\1>", "");
        // Replace <br>, <p>, <div> tags with newlines
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?i)</(p|div|tr|li)>", "\n");
        // Remove remaining HTML tags
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        // Decode common HTML entities
        cleaned = cleaned.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
        // Collapse multiple whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }
}
