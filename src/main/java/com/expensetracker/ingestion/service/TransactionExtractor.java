package com.expensetracker.ingestion.service;

import com.expensetracker.ingestion.dto.ParsedTransactionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI-powered fallback for transaction extraction.
 *
 * Called by {@link SmsParserService} when regex confidence < 0.7.
 * Sends the raw SMS text to the Gemini API and parses the JSON response
 * into a {@link ParsedTransactionDTO}.
 *
 * If the AI API key is not configured, or if the call fails, this service
 * gracefully returns an empty Optional so the caller can mark the SMS as FAILED.
 */
@Service
@Slf4j
public class TransactionExtractor {

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.api-url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TransactionExtractor() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extracts transaction fields from unstructured SMS text using the LLM API.
     *
     * @param smsText raw SMS message
     * @return Optional containing the parsed DTO, empty if extraction failed or API unavailable
     */
    public Optional<ParsedTransactionDTO> extract(String smsText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API key not configured — skipping AI extraction for SMS");
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(smsText);
            String rawJson = callGeminiApi(prompt);
            return parseAiResponse(rawJson, smsText);
        } catch (Exception e) {
            log.error("AI extraction failed for SMS '{}': {}", smsText, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private String buildPrompt(String smsText) {
        return """
                You are a financial data extractor. Extract transaction details from the following SMS message.
                
                SMS: "%s"
                
                Respond ONLY with a valid JSON object (no markdown, no explanation) using these exact fields:
                {
                  "amount": <number or null>,
                  "merchant": "<string or null>",
                  "date": "<ISO-8601 UTC string or null>",
                  "paymentMode": "<UPI|CARD|CASH|NET_BANKING or null>",
                  "upiPlatform": "<GPAY|PHONEPE|PAYTM|CRED|OTHER or null>",
                  "transactionType": "<EXPENSE|INCOME|TRANSFER>",
                  "refNumber": "<string or null>",
                  "bankName": "<string or null>",
                  "confidence": <0.0–1.0>
                }
                
                Rules:
                - amount must be a positive number (INR)
                - date must be in ISO-8601 UTC format, or null if not found
                - confidence reflects how certain you are (1.0 = very certain)
                - If this does not look like a financial transaction, return confidence 0.0
                """.formatted(smsText);
    }

    private String callGeminiApi(String prompt) {
        String url = String.format("%s/models/%s:generateContent?key=%s", apiUrl, model, apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", 512
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Gemini API returned status: " + response.getStatusCode());
        }

        // Extract the text content from the Gemini response envelope
        JsonNode root = parseJson(response.getBody());
        return root.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();
    }

    private Optional<ParsedTransactionDTO> parseAiResponse(String rawText, String smsText) {
        // Strip any accidental markdown code fences
        String cleaned = rawText
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        JsonNode node = parseJson(cleaned);

        double confidence = node.path("confidence").asDouble(0.0);
        if (confidence < 0.5) {
            log.debug("AI confidence too low ({}) for SMS, treating as FAILED", confidence);
            return Optional.empty();
        }

        Instant date = null;
        String rawDate = node.path("date").asText(null);
        if (rawDate != null && !rawDate.equals("null") && !rawDate.isBlank()) {
            try {
                date = Instant.parse(rawDate);
            } catch (Exception e) {
                log.debug("Could not parse AI-returned date '{}': {}", rawDate, e.getMessage());
            }
        }

        ParsedTransactionDTO dto = ParsedTransactionDTO.builder()
                .amount(node.path("amount").isMissingNode() || node.path("amount").isNull()
                        ? null : node.path("amount").asDouble())
                .merchant(nullableText(node, "merchant"))
                .date(date)
                .paymentMode(nullableText(node, "paymentMode"))
                .upiPlatform(nullableText(node, "upiPlatform"))
                .transactionType(node.path("transactionType").asText("EXPENSE"))
                .refNumber(nullableText(node, "refNumber"))
                .bankName(nullableText(node, "bankName"))
                .confidence(confidence)
                .rawText(smsText)
                .status("SUCCESS")
                .build();

        log.debug("AI extracted transaction: amount={}, merchant={}, confidence={}",
                dto.getAmount(), dto.getMerchant(), dto.getConfidence());

        return Optional.of(dto);
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return (text.equalsIgnoreCase("null") || text.isBlank()) ? null : text;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
