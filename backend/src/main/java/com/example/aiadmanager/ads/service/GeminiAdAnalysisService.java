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

    @Value("${app.gemini.model:gemini-2.5-flash}")
    private String model;

    public GeminiAdAnalysisService(AdCritiqueRepository repo) {
        this.repo = repo;
        this.webClient = WebClient.builder().build();
    }

    public AdCritique analyzeAndSave(byte[] imageBytes, String originalFilename, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("GEMINI_API_KEY missing.");
        }

        if (!StringUtils.hasText(contentType) ||
                !(contentType.equals("image/png") || contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Only PNG/JPG images are supported.");
        }

        String b64 = Base64.getEncoder().encodeToString(imageBytes);

        String prompt =
                "Return ONLY valid JSON (no markdown, no extra keys). " +
                "Shape: {overallScore:number,scores:{visualHierarchy:number,copyEffectiveness:number,colorTheory:number}," +
                "strengths:string[],issues:string[],actionableFixes:{title:string,why:string,how:string}[],improvedHeadlineOptions:string[]}. " +
                "Limits: strengths<=5, issues<=5, actionableFixes<=3, improvedHeadlineOptions<=5.";

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "role", "user",
                                "parts", new Object[]{
                                        Map.of("text", prompt),
                                        Map.of("inline_data", Map.of(
                                                "mime_type", contentType,
                                                "data", b64
                                        ))
                                }
                        )
                },
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json"
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

        String cleanJson = extractCleanJsonOrThrow(aiRaw);

        return repo.save(new AdCritique(
                StringUtils.hasText(originalFilename) ? originalFilename : "upload",
                contentType,
                b64,
                cleanJson
        ));
    }

    private String extractCleanJsonOrThrow(String aiRaw) {
        try {
            JsonNode root = mapper.readTree(aiRaw);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Gemini: no candidates");
            }

            JsonNode parts = candidates.path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new RuntimeException("Gemini: no parts");
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode p : parts) {
                if (p.has("text")) {
                    String t = p.path("text").asText("");
                    if (!t.isBlank()) sb.append(t);
                } else {
                    // sometimes structured json lands here
                    sb.append(p.toString());
                }
            }

            String txt = sb.toString().trim();

            txt = txt.replaceAll("(?s)^```json\\s*", "")
                     .replaceAll("(?s)^```\\s*", "")
                     .replaceAll("(?s)```\\s*$", "")
                     .trim();

            JsonNode critique = mapper.readTree(txt);

            if (critique.path("overallScore").isMissingNode()) {
                throw new RuntimeException("JSON valid but schema mismatch (missing overallScore)");
            }

            return txt;

        } catch (Exception ex) {
            throw new RuntimeException("Invalid / truncated Gemini JSON. Raw: " + aiRaw, ex);
        }
    }
}
