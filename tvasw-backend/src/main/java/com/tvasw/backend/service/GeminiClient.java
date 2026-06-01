package com.tvasw.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class GeminiClient {

    @Value("${gemini.endpoint:https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent}")
    private String endpoint;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            // fallback: simple echo / simulated response
            return "[SIMULATED GEMINI RESPONSE] Based on idea: " + prompt;
        }

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> contents = Map.of("parts", List.of(part));
        Map<String, Object> payload = Map.of("contents", List.of(contents));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> resp = rest.postForEntity(endpoint, request, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) return resp.getBody();
            return "[GEMINI ERROR] " + resp.getStatusCode().toString();
        } catch (Exception ex) {
            return "[GEMINI EXCEPTION] " + ex.getMessage();
        }
    }
}
