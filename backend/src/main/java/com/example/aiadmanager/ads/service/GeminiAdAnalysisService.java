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

        // 1) First attempt: strict JSON, forced non-empty arrays
        String prompt = buildPrimaryPrompt();
        String aiRaw = callGeminiWithImage(prompt, b64, contentType, 1400);

        // 2) Extract JSON safely + auto-repair if truncated
        String cleanJson = extractOrRepair(aiRaw);

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
                "Never return empty arrays. Keep each item concise (<= 160 chars).";
    }

    private String buildRepairPrompt(String brokenText) {
        return
                "You will be given a possibly TRUNCATED or INVALID JSON string. " +
                "Fix it into VALID JSON that matches the exact schema below. " +
                "Return ONLY the fixed JSON. No markdown. No explanations.\n\n" +
                "Schema EXACTLY:\n" +
                "{\n" +
                "  \"overallScore\": number,\n" +
                "  \"scores\": {\"visualHierarchy\": number, \"copyEffectiveness\": number, \"colorTheory\": number},\n" +
                "  \"strengths\": string[],\n" +
                "  \"issues\": string[],\n" +
                "  \"actionableFixes\": [{\"title\": string, \"why\": string, \"how\": string}],\n" +
                "  \"improvedHeadlineOptions\": string[]\n" +
                "}\n\n" +
                "Rules:\n" +
                "- Keep existing values if present.\n" +
                "- If a field is missing, add it, BUT never leave arrays empty.\n" +
                "- strengths MUST have exactly 5 items; issues MUST have exactly 5 items; actionableFixes MUST have exactly 3 items; improvedHeadlineOptions MUST have exactly 5 items.\n" +
                "- Enforce max limits: strengths<=5, issues<=5, fixes<=3, headlines<=5.\n" +
                "- Keep each item concise (<= 160 chars).\n\n" +
                "BROKEN_JSON:\n" +
                brokenText;
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

    private String extractOrRepair(String aiRaw) {
        JsonNode root;
        try {
            root = mapper.readTree(aiRaw);
        } catch (Exception e) {
            return repairFromBroken(aiRaw);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini: no candidates. Raw: " + aiRaw);
        }

        String text = extractPartsText(candidates.path(0).path("content").path("parts")).trim();
        String cleaned = stripCodeFences(text);

        if (isValidCritiqueJson(cleaned)) {
            return cleaned;
        }

        String broken = !cleaned.isBlank() ? cleaned : text;
        return repairFromBroken(broken);
    }

    private String repairFromBroken(String broken) {
        String repairPrompt = buildRepairPrompt(broken);
        String repairRaw = callGeminiTextOnly(repairPrompt, 1200);

        try {
            JsonNode root2 = mapper.readTree(repairRaw);
            JsonNode candidates2 = root2.path("candidates");
            if (!candidates2.isArray() || candidates2.isEmpty()) {
                throw new RuntimeException("Gemini repair: no candidates. Raw: " + repairRaw);
            }

            String repairedText = extractPartsText(candidates2.path(0).path("content").path("parts")).trim();
            repairedText = stripCodeFences(repairedText);

            if (!isValidCritiqueJson(repairedText)) {
                throw new RuntimeException("Gemini repair returned invalid JSON. Raw: " + repairRaw);
            }

            return repairedText;
        } catch (Exception ex) {
            throw new RuntimeException("Invalid / truncated Gemini JSON. Raw: " + broken, ex);
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

            // ALSO enforce non-empty arrays (your requirement)
            if (!critique.path("strengths").isArray() || critique.path("strengths").size() == 0) return false;
            if (!critique.path("issues").isArray() || critique.path("issues").size() == 0) return false;
            if (!critique.path("actionableFixes").isArray() || critique.path("actionableFixes").size() == 0) return false;
            if (!critique.path("improvedHeadlineOptions").isArray() || critique.path("improvedHeadlineOptions").size() == 0) return false;

            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
