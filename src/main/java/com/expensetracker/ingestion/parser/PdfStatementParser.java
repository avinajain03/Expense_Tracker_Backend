package com.expensetracker.ingestion.parser;

import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses PDF bank statements using Apache PDFBox.
 * Defines bank-specific parsing strategies for common Indian banks
 * and a generic fallback for unrecognised formats.
 */
@Component
@Slf4j
public class PdfStatementParser {

    // ── Common date formats used by Indian banks ──────────────────────
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    /**
     * Generic line pattern that captures:
     * group 1 — date (various formats)
     * group 2 — description / narration
     * group 3 — optional debit amount
     * group 4 — optional credit amount
     * group 5 — optional balance
     *
     * This intentionally uses a broad capture to handle varied bank layouts.
     */
    private static final Pattern GENERIC_LINE_PATTERN = Pattern.compile(
            "^(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{1,2}[\\s\\-][A-Za-z]{3}[\\s\\-]\\d{2,4})" + // date
            "\\s+(.+?)" +                                                                               // description
            "\\s+([\\d,]+\\.\\d{2}|\\s*)" +                                                            // debit or amount col
            "\\s+([\\d,]+\\.\\d{2}|\\s*)" +                                                            // credit or amount col
            "(?:\\s+([\\d,]+\\.\\d{2}))?\\s*$"                                                         // optional balance
    );

    // Bank-specific patterns (more precise when we know the layout)
    private static final Pattern HDFC_LINE = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{2,4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s*$"
    );

    private static final Pattern SBI_LINE = Pattern.compile(
            "^(\\d{2}\\s[A-Za-z]{3}\\s\\d{4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})?\\s+([\\d,]+\\.\\d{2})?\\s+([\\d,]+\\.\\d{2})\\s*$"
    );

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Parse a PDF bank statement file and return extracted transactions.
     *
     * @param file     uploaded PDF file
     * @param bankName bank identifier (HDFC, SBI, ICICI, AXIS, KOTAK, OTHER)
     * @return list of parsed transaction DTOs
     */
    public List<ParsedTransactionDTO> parse(MultipartFile file, String bankName) {
        String text = extractTextFromPdf(file);
        if (text == null || text.isBlank()) {
            log.warn("No text extracted from PDF: {}", file.getOriginalFilename());
            return List.of();
        }

        log.debug("Extracted {} characters from PDF '{}'", text.length(), file.getOriginalFilename());

        return switch (bankName.toUpperCase()) {
            case "HDFC"  -> parseHdfcStatement(text);
            case "SBI"   -> parseSbiStatement(text);
            case "ICICI" -> parseIciciStatement(text);
            case "AXIS"  -> parseAxisStatement(text);
            case "KOTAK" -> parseKotakStatement(text);
            default      -> parseGenericStatement(text, bankName);
        };
    }

    // ── PDF Text Extraction ──────────────────────────────────────────

    private String extractTextFromPdf(MultipartFile file) {
        try {
            PDDocument document = Loader.loadPDF(file.getBytes());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            document.close();
            return text;
        } catch (Exception e) {
            log.error("Failed to extract text from PDF '{}': {}", file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    // ── Bank-Specific Parsers ────────────────────────────────────────

    /**
     * HDFC Bank Statement Format:
     * Date | Narration | Chq./Ref.No | Value Dt | Withdrawal Amt. | Deposit Amt. | Closing Balance
     * Lines typically: dd/MM/yy  description  amount  amount
     */
    private List<ParsedTransactionDTO> parseHdfcStatement(String text) {
        List<ParsedTransactionDTO> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher m = HDFC_LINE.matcher(line);
            if (m.matches()) {
                ParsedTransactionDTO dto = buildFromMatch(
                        m.group(1), m.group(2), m.group(3), m.group(4),
                        "HDFC", 0.85);
                if (dto != null) results.add(dto);
                continue;
            }

            // Try generic pattern as fallback within HDFC
            ParsedTransactionDTO generic = tryGenericLine(line, "HDFC", 0.75);
            if (generic != null) results.add(generic);
        }

        log.info("HDFC PDF parser extracted {} transactions", results.size());
        return results;
    }

    /**
     * SBI Bank Statement Format:
     * Txn Date | Value Date | Description | Ref No./Chq No. | Debit | Credit | Balance
     * Date format: dd MMM yyyy
     */
    private List<ParsedTransactionDTO> parseSbiStatement(String text) {
        List<ParsedTransactionDTO> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher m = SBI_LINE.matcher(line);
            if (m.matches()) {
                String debit = m.group(3);
                String credit = m.group(4);
                ParsedTransactionDTO dto = buildFromDebitCredit(
                        m.group(1), m.group(2), debit, credit,
                        "SBI", 0.85);
                if (dto != null) results.add(dto);
                continue;
            }

            ParsedTransactionDTO generic = tryGenericLine(line, "SBI", 0.75);
            if (generic != null) results.add(generic);
        }

        log.info("SBI PDF parser extracted {} transactions", results.size());
        return results;
    }

    /**
     * ICICI Bank — uses generic parser with ICICI tag.
     * Format varies but generally: Date | Description | Debit | Credit | Balance
     */
    private List<ParsedTransactionDTO> parseIciciStatement(String text) {
        return parseGenericStatement(text, "ICICI");
    }

    /**
     * Axis Bank — uses generic parser with AXIS tag.
     */
    private List<ParsedTransactionDTO> parseAxisStatement(String text) {
        return parseGenericStatement(text, "AXIS");
    }

    /**
     * Kotak Mahindra Bank — uses generic parser with KOTAK tag.
     */
    private List<ParsedTransactionDTO> parseKotakStatement(String text) {
        return parseGenericStatement(text, "KOTAK");
    }

    // ── Generic / Fallback Parser ────────────────────────────────────

    /**
     * Generic parser that works across most Indian bank statement PDFs.
     * Looks for lines that start with a date and contain numeric amounts.
     */
    private List<ParsedTransactionDTO> parseGenericStatement(String text, String bankName) {
        List<ParsedTransactionDTO> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            ParsedTransactionDTO dto = tryGenericLine(line.trim(), bankName, 0.65);
            if (dto != null) {
                results.add(dto);
            }
        }

        log.info("Generic PDF parser ({}) extracted {} transactions", bankName, results.size());
        return results;
    }

    // ── Shared Helpers ───────────────────────────────────────────────

    private ParsedTransactionDTO tryGenericLine(String line, String bankName, double confidence) {
        if (line.isEmpty()) return null;

        Matcher m = GENERIC_LINE_PATTERN.matcher(line);
        if (!m.matches()) return null;

        String dateStr = m.group(1);
        String description = m.group(2).trim();
        String col1 = m.group(3) != null ? m.group(3).trim() : "";
        String col2 = m.group(4) != null ? m.group(4).trim() : "";

        // Skip header or summary lines
        if (description.toUpperCase().contains("OPENING BALANCE")
                || description.toUpperCase().contains("CLOSING BALANCE")
                || description.toUpperCase().contains("NARRATION")
                || description.toUpperCase().contains("DESCRIPTION")) {
            return null;
        }

        return buildFromDebitCredit(dateStr, description, col1, col2, bankName, confidence);
    }

    /**
     * Builds a DTO from separate debit/credit columns.
     * Non-empty debit → EXPENSE, non-empty credit → INCOME.
     */
    private ParsedTransactionDTO buildFromDebitCredit(String dateStr, String description,
                                                       String debit, String credit,
                                                       String bankName, double confidence) {
        Double amount = null;
        String transactionType = "EXPENSE";

        if (debit != null && !debit.isBlank()) {
            amount = parseAmount(debit);
            transactionType = "EXPENSE";
        }
        if ((amount == null || amount == 0.0) && credit != null && !credit.isBlank()) {
            amount = parseAmount(credit);
            transactionType = "INCOME";
        }
        if (amount == null || amount <= 0) return null;

        Instant date = parseDate(dateStr);

        // Extract merchant from description (first meaningful portion)
        String merchant = cleanDescription(description);

        return ParsedTransactionDTO.builder()
                .amount(amount)
                .merchant(merchant)
                .date(date)
                .transactionType(transactionType)
                .bankName(bankName)
                .paymentMode(detectPaymentMode(description))
                .upiPlatform(detectUpiPlatform(description))
                .confidence(confidence)
                .rawText(description)
                .status("SUCCESS")
                .build();
    }

    /**
     * Builds a DTO from a two-amount match (withdrawal amt, deposit amt).
     * Picks whichever column has the larger non-zero value.
     */
    private ParsedTransactionDTO buildFromMatch(String dateStr, String description,
                                                 String amount1, String amount2,
                                                 String bankName, double confidence) {
        Double withdrawal = parseAmount(amount1);
        Double deposit = parseAmount(amount2);

        Double amount;
        String transactionType;

        if (withdrawal != null && withdrawal > 0) {
            amount = withdrawal;
            transactionType = "EXPENSE";
        } else if (deposit != null && deposit > 0) {
            amount = deposit;
            transactionType = "INCOME";
        } else {
            return null;
        }

        Instant date = parseDate(dateStr);
        String merchant = cleanDescription(description);

        return ParsedTransactionDTO.builder()
                .amount(amount)
                .merchant(merchant)
                .date(date)
                .transactionType(transactionType)
                .bankName(bankName)
                .paymentMode(detectPaymentMode(description))
                .upiPlatform(detectUpiPlatform(description))
                .confidence(confidence)
                .rawText(description)
                .status("SUCCESS")
                .build();
    }

    private Double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return null;
        try {
            return Double.parseDouble(amountStr.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate ld = LocalDate.parse(dateStr, fmt);
                // Handle 2-digit years (e.g. 26 → 2026)
                if (ld.getYear() < 100) {
                    ld = ld.withYear(ld.getYear() + 2000);
                }
                return ld.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        log.debug("Could not parse date: '{}'", dateStr);
        return null;
    }

    /**
     * Cleans up the narration/description to extract a merchant-like value.
     * Removes common prefixes like UPI/, NEFT/, IMPS/ etc.
     */
    private String cleanDescription(String description) {
        if (description == null) return null;
        String cleaned = description
                .replaceAll("(?i)^(UPI|NEFT|RTGS|IMPS|BIL|ATM|POS)[/-]\\s*", "")
                .replaceAll("\\d{10,}", "")           // remove long numbers (ref IDs)
                .replaceAll("[/@]\\S+", "")            // remove UPI IDs
                .replaceAll("\\s+", " ")
                .trim();

        // Take the first meaningful segment (up to 50 chars)
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50).trim();
        }
        return cleaned.isEmpty() ? description.substring(0, Math.min(50, description.length())) : cleaned;
    }

    private String detectPaymentMode(String description) {
        if (description == null) return null;
        String upper = description.toUpperCase();
        if (upper.contains("UPI")) return "UPI";
        if (upper.contains("NEFT") || upper.contains("RTGS") || upper.contains("IMPS")) return "NET_BANKING";
        if (upper.contains("POS") || upper.contains("CARD")) return "CARD";
        if (upper.contains("ATM") || upper.contains("CASH")) return "CASH";
        return null;
    }

    private String detectUpiPlatform(String description) {
        if (description == null) return null;
        String upper = description.toUpperCase();
        if (upper.contains("GPAY") || upper.contains("GOOGLE PAY")) return "GPAY";
        if (upper.contains("PHONEPE") || upper.contains("PHONE PE")) return "PHONEPE";
        if (upper.contains("PAYTM")) return "PAYTM";
        if (upper.contains("CRED")) return "CRED";
        if (upper.contains("UPI")) return "OTHER";
        return null;
    }
}
