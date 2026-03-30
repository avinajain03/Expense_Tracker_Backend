package com.expensetracker.ingestion.parser;

import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless utility that attempts to parse Indian bank/UPI transaction SMS
 * messages using regex patterns. Each pattern targets a specific bank or UPI
 * app format. Returns an {@link Optional} containing the parsed data and a
 * confidence score, or empty if no pattern matches.
 */
@Component
public class SmsRegexPatterns {

    // ── Date formats found in Indian bank SMS ──────────────────────────
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),   // 08-Mar-26
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),  // 08-03-2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),  // 08/03/2026
            DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH),    // 08-03-26
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),    // 08/03/26
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH), // 08 Mar 2026
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)  // 08-Mar-2026
    );

    // ── Pattern record ─────────────────────────────────────────────────
    private record SmsPattern(Pattern regex, String bankName, String upiPlatform, double baseConfidence) {}

    // ── Bank / UPI patterns ────────────────────────────────────────────
    private static final List<SmsPattern> PATTERNS = List.of(

            // === GENERIC DEBIT PATTERNS (cover most Indian banks) ===

            // Pattern: "Rs.450.00 debited from A/C XX1234 on 08-Mar-26 via UPI to SWIGGY. Ref 412345678901"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s*(?:debited|spent|paid).*?" +
                            "(?:A/?C|Acct?|Account)\\s*(?:\\*{2,}|XX?)?(\\d{3,4}).*?" +
                            "(?:on\\s+)(\\d{1,2}[\\-/]\\w{3,9}[\\-/]\\d{2,4}).*?" +
                            "(?:to|at|@)\\s+([A-Za-z0-9 _\\-]+?)(?:\\.|,|\\s+Ref|\\s*$).*?" +
                            "(?:Ref\\.?\\s*#?\\s*(\\d+))?",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, null, 0.85),

            // Pattern: "INR 1,200.00 debited from HDFC Bank A/C **1234 on 08-03-2026 to AMAZON"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s*debited.*?" +
                            "(HDFC|SBI|ICICI|AXIS|KOTAK|BOB|PNB|CANARA|UNION|IDBI|INDUSIND).*?" +
                            "(?:on\\s+)(\\d{1,2}[\\-/]\\w{3,9}[\\-/]\\d{2,4}).*?" +
                            "(?:to|at)\\s+([A-Za-z0-9 _\\-]+?)(?:\\.|,|\\s|$)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, null, 0.80),

            // Pattern: "Your A/C XX1234 is debited with Rs.450.00 on 08-Mar-26 Info: UPI/412345/SWIGGY"
            new SmsPattern(
                    Pattern.compile(
                            "(?:A/?C|Acct?|Account)\\s*(?:\\*{2,}|XX?)?(\\d{3,4}).*?" +
                            "debited.*?(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*).*?" +
                            "(?:on\\s+)(\\d{1,2}[\\-/]\\w{3,9}[\\-/]\\d{2,4}).*?" +
                            "(?:Info:?|UPI/?|Ref:?)\\s*(?:\\d*/)?([A-Za-z0-9 _\\-]+?)(?:\\.|,|\\s|$)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, null, 0.80),

            // === CREDIT / INCOME PATTERNS ===

            // Pattern: "Rs.50,000.00 credited to your A/C XX1234 on 08-Mar-26. Ref 412345678901"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s*(?:credited|received|deposited).*?" +
                            "(?:A/?C|Acct?|Account)\\s*(?:\\*{2,}|XX?)?(\\d{3,4}).*?" +
                            "(?:on\\s+)(\\d{1,2}[\\-/]\\w{3,9}[\\-/]\\d{2,4}).*?" +
                            "(?:Ref\\.?\\s*#?\\s*(\\d+))?",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, null, 0.75),

            // Credit Card: "INR 10152.00 was spent at MAKEMYTRIP INDIA PVT NEW DELHI on 09-MAR-2020 on your ... Credit Card"
            // group(1)=amount, group(2)=merchant — date is non-capturing, routes through UPI branch
            new SmsPattern(
                    Pattern.compile(
                            "(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s+was\\s+(?:spent|used|debited)\\s+at\\s+" +
                            "([A-Za-z0-9][A-Za-z0-9 ,\\.]*?)\\s+(?:IND\\s+)?on\\s+" +
                            "(?:\\d{1,2}[\\-/]\\w{3,9}[\\-/]\\d{2,4}).*?" +
                            "(?:Credit Card|Debit Card|Card)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, "CARD", 0.87),

            // === UPI-SPECIFIC PATTERNS ===

            // GPay pattern: "Paid Rs.450 to SWIGGY using Google Pay. UPI Ref: 412345678901"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Paid|Sent)\\s+(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s+" +
                            "(?:to)\\s+([A-Za-z0-9 _\\-]+?)\\s+" +
                            "(?:using|via|on)\\s+(?:Google Pay|GPay).*?" +
                            "(?:UPI\\s*Ref:?\\s*#?\\s*(\\d+))?",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, "GPAY", 0.90),

            // PhonePe pattern: "Rs 450 paid to SWIGGY via PhonePe. UPI Ref No. 412345678901"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Rs\\.?|INR\\s?)\\s*([\\d,]+\\.?\\d*)\\s+(?:paid|sent)\\s+" +
                            "(?:to)\\s+([A-Za-z0-9 _\\-]+?)\\s+" +
                            "(?:using|via|on)\\s+PhonePe.*?" +
                            "(?:Ref\\.?\\s*(?:No\\.?)?\\s*#?\\s*(\\d+))?",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, "PHONEPE", 0.90),

            // Paytm pattern: "You paid Rs.450 to SWIGGY from Paytm UPI. Ref 412345678901"
            new SmsPattern(
                    Pattern.compile(
                            "(?:You\\s+)?(?:paid|sent)\\s+(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s+" +
                            "(?:to)\\s+([A-Za-z0-9 _\\-]+?)\\s+" +
                            "(?:from|via|on)\\s+Paytm.*?" +
                            "(?:Ref\\.?\\s*#?\\s*(\\d+))?",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, "PAYTM", 0.90),

            // CRED pattern: "Payment of Rs.450 made to SWIGGY via CRED UPI"
            new SmsPattern(
                    Pattern.compile(
                            "(?:Payment of|Paid)\\s+(?:Rs\\.?|INR\\s?)([\\d,]+\\.?\\d*)\\s+" +
                            "(?:made\\s+)?(?:to)\\s+([A-Za-z0-9 _\\-]+?)\\s+" +
                            "(?:via|on|using)\\s+CRED",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    null, "CRED", 0.90),

            // === AXIS / GENERIC BANK UPI MULTI-LINE PATTERNS ===

            // Axis Bank UPI multi-line: "INR 6720 debited\nA/c no. XXXXXX47845\n23-03-26, 23:52:42\nUPI/P2A/402832007488/BUDIMUDI SRINIVASA"
            // group(1)=amount, group(2)=merchant — date and ref use non-capturing groups to align with UPI branch
            new SmsPattern(
                    Pattern.compile(
                            "(?:INR|Rs\\.?)\\s*([\\d,]+\\.?\\d*)\\s*debited.*?" +
                            "(?:\\d{2}[\\-/]\\d{2}[\\-/]\\d{2,4}).*?" +
                            "UPI/(?:P2A|P2M|P2P|UPI)/(?:[A-Za-z0-9]+)/([A-Za-z][A-Za-z0-9 ]*)",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                    "AXIS", "OTHER", 0.88)
    );

    /**
     * Attempt to parse the given SMS text against all known patterns.
     *
     * @param smsText raw SMS message body
     * @return parsed result with confidence, or empty if no pattern matched
     */
    public Optional<ParsedTransactionDTO> parse(String smsText) {
        if (smsText == null || smsText.isBlank()) {
            return Optional.empty();
        }

        for (SmsPattern sp : PATTERNS) {
            Matcher m = sp.regex.matcher(smsText);
            if (m.find()) {
                try {
                    ParsedTransactionDTO dto = extractFromMatch(m, sp, smsText);
                    if (dto != null && dto.getAmount() != null && dto.getAmount() > 0) {
                        return Optional.of(dto);
                    }
                } catch (Exception ignored) {
                    // Pattern matched but extraction failed — try next pattern
                }
            }
        }
        return Optional.empty();
    }

    // ── Private helpers ────────────────────────────────────────────────

    private ParsedTransactionDTO extractFromMatch(Matcher m, SmsPattern sp, String rawText) {
        // Different patterns have groups in different order; normalise here
        Double amount = null;
        String merchant = null;
        String dateStr = null;
        String refNumber = null;

        if (sp.upiPlatform != null) {
            // UPI-specific patterns: group(1)=amount, group(2)=merchant, group(3)=ref
            amount = parseAmount(m.group(1));
            merchant = cleanMerchant(m.group(2));
            refNumber = safeGroup(m, 3);
        } else if (rawText.toLowerCase().contains("credited") ||
                   rawText.toLowerCase().contains("received") ||
                   rawText.toLowerCase().contains("deposited")) {
            // Credit patterns: group(1)=amount, group(2)=account, group(3)=date, group(4)=ref
            amount = parseAmount(m.group(1));
            dateStr = safeGroup(m, 3);
            refNumber = safeGroup(m, 4);
        } else if (m.pattern().toString().contains("debited.*?Rs")) {
            // Inverse pattern: group(1)=account, group(2)=amount, group(3)=date, group(4)=merchant
            amount = parseAmount(m.group(2));
            dateStr = safeGroup(m, 3);
            merchant = cleanMerchant(safeGroup(m, 4));
        } else {
            // Standard debit: group(1)=amount, group(2)=account/bank, group(3)=date, group(4)=merchant, group(5)=ref
            amount = parseAmount(m.group(1));
            dateStr = safeGroup(m, 3);
            merchant = cleanMerchant(safeGroup(m, 4));
            refNumber = safeGroup(m, 5);
        }

        // Detect bank name from SMS content
        String bankName = sp.bankName != null ? sp.bankName : detectBank(rawText);

        // Detect UPI platform from SMS content if not already set by pattern
        String upiPlatform = sp.upiPlatform != null ? sp.upiPlatform : detectUpiPlatform(rawText);

        // Determine transaction type
        String transactionType = isCredit(rawText) ? "INCOME" : "EXPENSE";

        return ParsedTransactionDTO.builder()
                .amount(amount)
                .merchant(merchant)
                .date(parseDate(dateStr))
                .refNumber(refNumber)
                .bankName(bankName)
                .upiPlatform(upiPlatform)
                .paymentMode("CARD".equalsIgnoreCase(upiPlatform) ? "CARD" : upiPlatform != null ? "UPI" : null)
                .transactionType(transactionType)
                .confidence(sp.baseConfidence)
                .rawText(rawText)
                .status("SUCCESS")
                .build();
    }

    private Double parseAmount(String raw) {
        if (raw == null) return null;
        return Double.parseDouble(raw.replace(",", ""));
    }

    private Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(raw.trim(), fmt);
                return ld.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
            } catch (DateTimeParseException ignored) {}
        }
        return Instant.now();
    }

    private String cleanMerchant(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("[\\s]+", " ");
    }

    private String safeGroup(Matcher m, int group) {
        try {
            return m.group(group);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private boolean isCredit(String text) {
        String lower = text.toLowerCase();
        return lower.contains("credited") || lower.contains("received") || lower.contains("deposited");
    }

    private String detectBank(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("HDFC"))      return "HDFC";
        if (upper.contains("SBI"))       return "SBI";
        if (upper.contains("ICICI"))     return "ICICI";
        if (upper.contains("AXIS"))      return "AXIS";
        if (upper.contains("KOTAK"))     return "KOTAK";
        if (upper.contains("BOB"))       return "BOB";
        if (upper.contains("PNB"))       return "PNB";
        if (upper.contains("CANARA"))    return "CANARA";
        if (upper.contains("INDUSIND"))  return "INDUSIND";
        if (upper.contains("IDBI"))      return "IDBI";
        if (upper.contains("UNION"))     return "UNION";
        return null;
    }

    private String detectUpiPlatform(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("GOOGLE PAY") || upper.contains("GPAY"))   return "GPAY";
        if (upper.contains("PHONEPE"))   return "PHONEPE";
        if (upper.contains("PAYTM"))     return "PAYTM";
        if (upper.contains("CRED"))      return "CRED";
        if (upper.contains("UPI"))       return "OTHER";
        return null;
    }
}
