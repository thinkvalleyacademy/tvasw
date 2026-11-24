package com.tvasw.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final int MAX_ATTEMPTS = 4;

    @Value("${gemini.endpoint}")
    private String endpoint;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate rest;

    public GeminiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.rest = new RestTemplate(factory);
    }

    public String generate(String prompt) {
        return generate(prompt, Map.of());
    }

    public String generateJson(String prompt, Map<String, Object> schema) {
        return generate(prompt, Map.of("responseMimeType", "application/json", "responseSchema", schema));
    }

    private String generate(String prompt, Map<String, Object> config) {
        if (apiKey == null || apiKey.isBlank()) {
            // fallback: simple echo / simulated response
            return "[SIMULATED GEMINI RESPONSE] Based on idea: " + prompt;
        }

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> contents = Map.of("parts", List.of(part));
        Map<String, Object> payload = Map.of("contents", List.of(contents), "generationConfig", config);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-goog-api-key", apiKey.trim());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        JsonNode body;
        try {
            body = post(request);
        } catch (RestClientResponseException ex) {
            log.error("Gemini returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            HttpStatus status = ex.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "AI service error");
        } catch (RestClientException ex) {
            log.error("Gemini request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
        }

        String text = extractText(body);
        if (text.isBlank()) {
            log.error("Gemini returned no text: {}", body);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned no text");
        }
        return text;
    }

    // Gemini returns 503 during short demand spikes, so retry those
    private JsonNode post(HttpEntity<Map<String, Object>> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return rest.postForObject(endpoint, request, JsonNode.class);
            } catch (HttpServerErrorException.ServiceUnavailable ex) {
                if (attempt == MAX_ATTEMPTS) throw ex;
                log.warn("Gemini busy, retrying (attempt {} of {})", attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(1000L << attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private String extractText(JsonNode body) {
        StringBuilder text = new StringBuilder();
        if (body != null) {
            for (JsonNode part : body.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) {
                    text.append(part.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }
}
