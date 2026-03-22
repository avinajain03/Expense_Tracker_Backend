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
 * AI-powered fallback for transaction extraction using the OpenAI API.
 *
 * Called by {@link SmsParserService} when regex confidence < 0.7.
 * Sends the raw SMS text to OpenAI and parses the JSON response
 * into a {@link ParsedTransactionDTO}.
 *
 * Gracefully returns an empty Optional if:
 * - OPENAI_API_KEY is not set
 * - The API call fails
 * - AI confidence is below 0.5
 */
@Service
@Slf4j
public class TransactionExtractor {

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.api-url:https://api.openai.com/v1}")
    private String apiUrl;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TransactionExtractor() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extracts transaction fields from unstructured SMS text using OpenAI.
     *
     * @param smsText raw SMS message
     * @return Optional containing the parsed DTO, or empty if extraction failed
     */
    public Optional<ParsedTransactionDTO> extract(String smsText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key not configured — skipping AI extraction for SMS");
            return Optional.empty();
        }

        try {
            String rawJson = callOpenAiApi(smsText);
            return parseAiResponse(rawJson, smsText);
        } catch (Exception e) {
            log.error("AI extraction failed for SMS '{}': {}", smsText, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private String callOpenAiApi(String smsText) {
        String url = apiUrl + "/chat/completions";

        String systemPrompt = "You are a financial data extractor. " +
                "Extract transaction details from SMS messages and return ONLY valid JSON, no markdown or explanation.";

        String userPrompt = """
                Extract transaction details from this SMS:
                "%s"

                Return ONLY a JSON object with these exact fields:
                {
                  "amount": <positive number in INR, or null>,
                  "merchant": "<payee/merchant name, or null>",
                  "date": "<ISO-8601 UTC datetime string, or null>",
                  "paymentMode": "<UPI|CARD|CASH|NET_BANKING, or null>",
                  "upiPlatform": "<GPAY|PHONEPE|PAYTM|CRED|OTHER, or null>",
                  "transactionType": "<EXPENSE|INCOME|TRANSFER>",
                  "refNumber": "<reference/transaction ID, or null>",
                  "bankName": "<bank name, or null>",
                  "confidence": <0.0–1.0, how confident you are this is a valid financial transaction>
                }
                """.formatted(smsText);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.1,
                "max_tokens", 512,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("OpenAI API returned status: " + response.getStatusCode());
        }

        // Extract the message content from the OpenAI response envelope
        JsonNode root = parseJson(response.getBody());
        return root.path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText();
    }

    private Optional<ParsedTransactionDTO> parseAiResponse(String rawContent, String smsText) {
        JsonNode node = parseJson(rawContent.trim());

        double confidence = node.path("confidence").asDouble(0.0);
        if (confidence < 0.5) {
            log.debug("OpenAI confidence too low ({}) for SMS, treating as FAILED", confidence);
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

        log.debug("OpenAI extracted transaction: amount={}, merchant={}, confidence={}",
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
