package com.example.aiadmanager.ads.service;

import com.example.aiadmanager.ads.model.AdCritique;
import com.example.aiadmanager.ads.repo.AdCritiqueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.LinkedHashMap;
import java.util.List;
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

        // 1) Analyze image (JSON required)
        String aiRaw = callGeminiWithImage(buildPrimaryPrompt(), b64, contentType, 1200);

        // 2) Try parse; if fails, regenerate clean JSON from scratch
        String cleanJson = extractOrRegenerate(aiRaw);

        return repo.save(new AdCritique(
                StringUtils.hasText(originalFilename) ? originalFilename : "upload",
                contentType,
                b64,
                cleanJson
        ));
    }

    private String buildPrimaryPrompt() {
        return
                "Return ONLY valid JSON (no markdown, no extra keys). " +
                "Schema EXACTLY: {overallScore:number,scores:{visualHierarchy:number,copyEffectiveness:number,colorTheory:number}," +
                "strengths:string[],issues:string[],actionableFixes:{title:string,why:string,how:string}[],improvedHeadlineOptions:string[]}. " +
                "HARD RULES: strengths MUST have exactly 5 items; issues MUST have exactly 5 items; " +
                "actionableFixes MUST have exactly 3 items; improvedHeadlineOptions MUST have exactly 5 items. " +
                "Never return empty arrays. Keep each item concise (<= 140 chars).";
    }

    private String buildRegeneratePromptFromBroken(String brokenMaybeJson) {
        // Key change: we are NOT "repairing" partial JSON anymore.
        // We are asking Gemini to generate a complete valid JSON from scratch.
        return
                "The previous output was TRUNCATED/INVALID. Ignore it and generate a fresh response from scratch.\n" +
                "Return ONLY valid JSON (no markdown, no extra keys).\n" +
                "Schema EXACTLY:\n" +
                "{\n" +
                "  \"overallScore\": number,\n" +
                "  \"scores\": {\"visualHierarchy\": number, \"copyEffectiveness\": number, \"colorTheory\": number},\n" +
                "  \"strengths\": string[5],\n" +
                "  \"issues\": string[5],\n" +
                "  \"actionableFixes\": [{\"title\": string, \"why\": string, \"how\": string}][3],\n" +
                "  \"improvedHeadlineOptions\": string[5]\n" +
                "}\n" +
                "Rules:\n" +
                "- strengths MUST be 5 items (no empty).\n" +
                "- issues MUST be 5 items (no empty).\n" +
                "- actionableFixes MUST be 3 items (no empty).\n" +
                "- improvedHeadlineOptions MUST be 5 items (no empty).\n" +
                "- Keep each item <= 140 chars.\n\n" +
                "TRUNCATED_OUTPUT_FOR_REFERENCE (ignore it if needed):\n" +
                brokenMaybeJson;
    }

    private String callGeminiWithImage(String prompt, String imageB64, String contentType, int maxOutputTokens) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inlineData", Map.of(
                                        "mimeType", contentType,
                                        "data", imageB64
                                ))
                        )
                )
        ));

        body.put("generationConfig", Map.of(
                "temperature", 0.2,
                "maxOutputTokens", maxOutputTokens,
                "responseMimeType", "application/json"
        ));

        try {
            return webClient.post()
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
    }

    private String callGeminiTextOnly(String prompt, int maxOutputTokens) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )
        ));

        body.put("generationConfig", Map.of(
                "temperature", 0.1,
                "maxOutputTokens", maxOutputTokens,
                "responseMimeType", "application/json"
        ));

        try {
            return webClient.post()
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
    }

    private String extractOrRegenerate(String aiRaw) {
        // Try to extract text from wrapper then parse JSON.
        String candidateText = extractCandidateText(aiRaw);

        // If already valid JSON, return it
        if (isValidCritiqueJson(candidateText)) {
            return candidateText;
        }

        // If invalid/truncated, regenerate from scratch (text-only)
        String regenPrompt = buildRegeneratePromptFromBroken(candidateText.isBlank() ? aiRaw : candidateText);
        String regenRaw = callGeminiTextOnly(regenPrompt, 1400);

        String regenText = extractCandidateText(regenRaw);
        if (!isValidCritiqueJson(regenText)) {
            throw new RuntimeException("Invalid / truncated Gemini JSON even after regenerate. Raw: " + regenRaw);
        }
        return regenText;
    }

    private String extractCandidateText(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) return "";

            String text = extractPartsText(candidates.path(0).path("content").path("parts")).trim();
            return stripCodeFences(text);
        } catch (Exception e) {
            // raw might already be plain JSON string
            return stripCodeFences(raw == null ? "" : raw.trim());
        }
    }

    private String extractPartsText(JsonNode parts) {
        if (!parts.isArray() || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : parts) {
            if (p.has("text")) {
                String t = p.path("text").asText("");
                if (!t.isBlank()) sb.append(t);
            } else {
                sb.append(p.toString());
            }
        }
        return sb.toString();
    }

    private String stripCodeFences(String txt) {
        if (txt == null) return "";
        return txt.replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
    }

    private boolean isValidCritiqueJson(String txt) {
        try {
            JsonNode critique = mapper.readTree(txt);

            if (!critique.has("overallScore")) return false;
            if (!critique.has("scores")) return false;
            if (!critique.has("strengths")) return false;
            if (!critique.has("issues")) return false;
            if (!critique.has("actionableFixes")) return false;
            if (!critique.has("improvedHeadlineOptions")) return false;

            if (!critique.path("strengths").isArray() || critique.path("strengths").size() < 5) return false;
            if (!critique.path("issues").isArray() || critique.path("issues").size() < 5) return false;
            if (!critique.path("actionableFixes").isArray() || critique.path("actionableFixes").size() < 3) return false;
            if (!critique.path("improvedHeadlineOptions").isArray() || critique.path("improvedHeadlineOptions").size() < 5) return false;

            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
