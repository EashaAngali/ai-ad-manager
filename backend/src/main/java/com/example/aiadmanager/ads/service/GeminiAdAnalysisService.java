package com.example.aiadmanager.ads.service;

import com.example.aiadmanager.ads.model.AdCritique;
import com.example.aiadmanager.ads.repo.AdCritiqueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class GeminiAdAnalysisService {

    private final AdCritiqueRepository repo;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.gemini.apiKey:}")
    private String apiKey;

    // Use model names like: gemini-2.5-flash or gemini-2.5-flash-lite
    @Value("${app.gemini.model:gemini-2.5-flash}")
    private String model;

    public GeminiAdAnalysisService(AdCritiqueRepository repo) {
        this.repo = repo;
        this.webClient = WebClient.builder().build();
    }

    public AdCritique analyzeAndSave(byte[] imageBytes, String originalFilename, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("GEMINI_API_KEY missing. Set it as an environment variable.");
        }

        if (!StringUtils.hasText(contentType) ||
                !(contentType.equals("image/png") || contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Only PNG/JPG images are supported.");
        }

        String b64 = Base64.getEncoder().encodeToString(imageBytes);

        String prompt =
                "Analyze this advertisement for visual hierarchy, copy effectiveness, and color theory. " +
                "Return STRICT JSON only (no markdown, no extra text). " +
                "Schema:\n" +
                "{\n" +
                "  \"overallScore\": number,\n" +
                "  \"scores\": {\"visualHierarchy\": number, \"copyEffectiveness\": number, \"colorTheory\": number},\n" +
                "  \"strengths\": string[],\n" +
                "  \"issues\": string[],\n" +
                "  \"actionableFixes\": [{\"title\": string, \"why\": string, \"how\": string}],\n" +
                "  \"improvedHeadlineOptions\": string[]\n" +
                "}";

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "role", "user",
                                "parts", new Object[]{
                                        Map.of("text", prompt),
                                        Map.of("inlineData", Map.of(
                                                "mimeType", contentType,
                                                "data", b64
                                        ))
                                }
                        )
                },
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 1800
                )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        String aiRaw;
        try {
            aiRaw = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Gemini API failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Gemini call failed: " + e.getMessage(), e);
        }

        String cleanJson = extractCleanJson(aiRaw);

        AdCritique saved = repo.save(new AdCritique(
                StringUtils.hasText(originalFilename) ? originalFilename : "upload",
                contentType,
                b64,
                cleanJson
        ));

        return saved;
    }

    private String extractCleanJson(String aiRaw) {
        try {
            JsonNode root = mapper.readTree(aiRaw);

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() == 0) {
                return aiRaw;
            }

            JsonNode parts0 = candidates.path(0).path("content").path("parts").path(0);

            // most common: {"text": "{...json...}"}
            String txt = parts0.path("text").asText(null);

            if (txt == null || txt.isBlank()) {
                return aiRaw;
            }

            txt = txt.trim();

            // if returned as a quoted JSON string, unescape it
            if (txt.startsWith("\"") && txt.endsWith("\"")) {
                txt = mapper.readValue(txt, String.class);
            }

            // validate final json
            mapper.readTree(txt);
            return txt;

        } catch (Exception ex) {
            return aiRaw;
        }
    }
}
