package com.expensetracker.ingestion.parser;

import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses CSV and Excel (.xlsx) bank statement files.
 * Auto-detects column headers and maps them to transaction fields.
 */
@Component
@Slf4j
public class CsvStatementParser {

    // ── Known column header patterns ─────────────────────────────────
    private static final List<String> DATE_HEADERS = List.of(
            "date", "txn date", "transaction date", "value date", "posting date", "txn. date"
    );
    private static final List<String> DESCRIPTION_HEADERS = List.of(
            "description", "narration", "particulars", "details", "transaction details",
            "remarks", "transaction description"
    );
    private static final List<String> DEBIT_HEADERS = List.of(
            "debit", "withdrawal", "withdrawal amt", "withdrawal amt.", "dr", "debit amount",
            "debit amt", "amount debited"
    );
    private static final List<String> CREDIT_HEADERS = List.of(
            "credit", "deposit", "deposit amt", "deposit amt.", "cr", "credit amount",
            "credit amt", "amount credited"
    );
    private static final List<String> AMOUNT_HEADERS = List.of(
            "amount", "transaction amount", "txn amount", "amt"
    );
    private static final List<String> BALANCE_HEADERS = List.of(
            "balance", "closing balance", "available balance", "running balance"
    );
    private static final List<String> REF_HEADERS = List.of(
            "ref", "reference", "ref no", "ref no.", "reference no", "reference number",
            "chq./ref.no", "chq/ref no", "transaction id", "utr"
    );

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH),
    };

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Parse a CSV file and return extracted transactions.
     */
    public List<ParsedTransactionDTO> parseCsv(MultipartFile file, String bankName) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) return List.of();

            return processRows(allRows, bankName, file.getOriginalFilename());
        } catch (IOException | CsvException e) {
            log.error("Failed to parse CSV '{}': {}", file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse an Excel (.xlsx) file and return extracted transactions.
     */
    public List<ParsedTransactionDTO> parseExcel(MultipartFile file, String bankName) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String[]> allRows = new ArrayList<>();

            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                    // Handle date cells specially
                    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        LocalDate ld = cell.getLocalDateTimeCellValue().toLocalDate();
                        cells.add(ld.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    } else {
                        cells.add(formatter.formatCellValue(cell).trim());
                    }
                }
                allRows.add(cells.toArray(String[]::new));
            }

            if (allRows.isEmpty()) return List.of();
            return processRows(allRows, bankName, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to parse Excel '{}': {}", file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    // ── Internal Processing ──────────────────────────────────────────

    /**
     * Processes rows from either CSV or Excel, auto-detecting the header row
     * and mapping columns to transaction fields.
     */
    private List<ParsedTransactionDTO> processRows(List<String[]> allRows, String bankName, String fileName) {
        // Find header row (could be first row, or after some metadata lines)
        int headerIndex = findHeaderRow(allRows);
        if (headerIndex < 0) {
            log.warn("Could not find header row in '{}' — attempting positional parsing", fileName);
            return parseWithoutHeaders(allRows, bankName);
        }

        String[] headers = allRows.get(headerIndex);
        ColumnMapping mapping = mapColumns(headers);

        if (mapping.dateCol < 0) {
            log.warn("No date column found in '{}' — cannot parse", fileName);
            return List.of();
        }

        List<ParsedTransactionDTO> results = new ArrayList<>();
        double confidence = mapping.allMapped() ? 0.85 : 0.65;

        for (int i = headerIndex + 1; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            ParsedTransactionDTO dto = parseRow(row, mapping, bankName, confidence);
            if (dto != null) {
                results.add(dto);
            }
        }

        log.info("CSV/Excel parser ({}) extracted {} transactions from '{}'",
                bankName, results.size(), fileName);
        return results;
    }

    /**
     * Attempts to find the header row by checking which row best matches known header names.
     */
    private int findHeaderRow(List<String[]> rows) {
        int bestIndex = -1;
        int bestScore = 0;

        int searchLimit = Math.min(rows.size(), 15); // headers usually within first 15 rows
        for (int i = 0; i < searchLimit; i++) {
            int score = scoreAsHeader(rows.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        // Require at least 2 matching column headers
        return bestScore >= 2 ? bestIndex : -1;
    }

    /**
     * Scores a row based on how many cells match known header patterns.
     */
    private int scoreAsHeader(String[] row) {
        int score = 0;
        for (String cell : row) {
            String lower = cell.toLowerCase().trim();
            if (DATE_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (DESCRIPTION_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (DEBIT_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (CREDIT_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (AMOUNT_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (BALANCE_HEADERS.stream().anyMatch(lower::contains)) score++;
            if (REF_HEADERS.stream().anyMatch(lower::contains)) score++;
        }
        return score;
    }

    /**
     * Maps detected header positions to a ColumnMapping record.
     */
    private ColumnMapping mapColumns(String[] headers) {
        int dateCol = -1, descCol = -1, debitCol = -1, creditCol = -1;
        int amountCol = -1, balanceCol = -1, refCol = -1;

        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().trim();

            if (dateCol < 0 && DATE_HEADERS.stream().anyMatch(h::contains)) dateCol = i;
            else if (descCol < 0 && DESCRIPTION_HEADERS.stream().anyMatch(h::contains)) descCol = i;
            else if (debitCol < 0 && DEBIT_HEADERS.stream().anyMatch(h::contains)) debitCol = i;
            else if (creditCol < 0 && CREDIT_HEADERS.stream().anyMatch(h::contains)) creditCol = i;
            else if (amountCol < 0 && AMOUNT_HEADERS.stream().anyMatch(h::contains)) amountCol = i;
            else if (balanceCol < 0 && BALANCE_HEADERS.stream().anyMatch(h::contains)) balanceCol = i;
            else if (refCol < 0 && REF_HEADERS.stream().anyMatch(h::contains)) refCol = i;
        }

        return new ColumnMapping(dateCol, descCol, debitCol, creditCol, amountCol, balanceCol, refCol);
    }

    /**
     * Parses a single data row using the detected column mapping.
     */
    private ParsedTransactionDTO parseRow(String[] row, ColumnMapping mapping,
                                           String bankName, double confidence) {
        if (row.length <= mapping.dateCol) return null;

        String dateStr = safeGet(row, mapping.dateCol);
        String description = safeGet(row, mapping.descCol);
        String debitStr = safeGet(row, mapping.debitCol);
        String creditStr = safeGet(row, mapping.creditCol);
        String amountStr = safeGet(row, mapping.amountCol);
        String refNumber = safeGet(row, mapping.refCol);

        // Skip empty or header-like rows
        if (dateStr == null || dateStr.isBlank()) return null;

        Instant date = parseDate(dateStr);
        if (date == null) return null; // not a valid data row

        // Determine amount and type
        Double amount = null;
        String transactionType = "EXPENSE";

        if (mapping.debitCol >= 0 && mapping.creditCol >= 0) {
            // Separate debit/credit columns
            Double debit = parseAmount(debitStr);
            Double credit = parseAmount(creditStr);

            if (debit != null && debit > 0) {
                amount = debit;
                transactionType = "EXPENSE";
            } else if (credit != null && credit > 0) {
                amount = credit;
                transactionType = "INCOME";
            }
        } else if (mapping.amountCol >= 0) {
            // Single amount column — negative = debit, positive = credit
            amount = parseAmount(amountStr);
            if (amount != null && amount < 0) {
                amount = Math.abs(amount);
                transactionType = "EXPENSE";
            } else if (amount != null) {
                transactionType = "INCOME";
            }
        }

        if (amount == null || amount <= 0) return null;

        String merchant = cleanDescription(description);

        return ParsedTransactionDTO.builder()
                .amount(amount)
                .merchant(merchant)
                .date(date)
                .transactionType(transactionType)
                .bankName(bankName)
                .paymentMode(detectPaymentMode(description))
                .upiPlatform(detectUpiPlatform(description))
                .refNumber(refNumber)
                .confidence(confidence)
                .rawText(description != null ? description : "")
                .status("SUCCESS")
                .build();
    }

    /**
     * Fallback: parse rows without detected headers, assuming common column positions.
     * Assumes: Col 0 = Date, Col 1 = Description, Col 2 = Debit, Col 3 = Credit
     */
    private List<ParsedTransactionDTO> parseWithoutHeaders(List<String[]> rows, String bankName) {
        ColumnMapping fallback = new ColumnMapping(0, 1, 2, 3, -1, -1, -1);
        List<ParsedTransactionDTO> results = new ArrayList<>();

        for (String[] row : rows) {
            ParsedTransactionDTO dto = parseRow(row, fallback, bankName, 0.5);
            if (dto != null) {
                results.add(dto);
            }
        }

        log.info("Fallback CSV parser ({}) extracted {} transactions", bankName, results.size());
        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private record ColumnMapping(int dateCol, int descCol, int debitCol, int creditCol,
                                  int amountCol, int balanceCol, int refCol) {
        boolean allMapped() {
            return dateCol >= 0 && descCol >= 0 && (debitCol >= 0 || amountCol >= 0);
        }
    }

    private String safeGet(String[] row, int index) {
        if (index < 0 || index >= row.length) return null;
        String val = row[index].trim();
        return val.isEmpty() ? null : val;
    }

    private Double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return null;
        try {
            // Remove commas, currency symbols, and whitespace
            String cleaned = amountStr
                    .replace(",", "")
                    .replace("₹", "")
                    .replace("INR", "")
                    .replace("Rs.", "")
                    .replace("Rs", "")
                    .trim();
            if (cleaned.isEmpty()) return null;
            return Double.parseDouble(cleaned);
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
                if (ld.getYear() < 100) {
                    ld = ld.withYear(ld.getYear() + 2000);
                }
                return ld.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private String cleanDescription(String description) {
        if (description == null) return null;
        String cleaned = description
                .replaceAll("(?i)^(UPI|NEFT|RTGS|IMPS|BIL|ATM|POS)[/-]\\s*", "")
                .replaceAll("\\d{10,}", "")
                .replaceAll("[/@]\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50).trim();
        }
        return cleaned.isEmpty()
                ? description.substring(0, Math.min(50, description.length()))
                : cleaned;
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
