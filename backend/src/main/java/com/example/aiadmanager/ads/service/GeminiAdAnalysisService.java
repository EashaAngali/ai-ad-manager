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
            throw new IllegalStateException("GEMINI_API_KEY missing. Set it as an environment variable.");
        }

        if (!StringUtils.hasText(contentType) ||
                !(contentType.equals("image/png") || contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Only PNG/JPG images are supported.");
        }

        String b64 = Base64.getEncoder().encodeToString(imageBytes);

        // IMPORTANT: keep it short to avoid MAX_TOKENS
        String prompt =
                "You are an ad analysis engine.\n" +
                "Return ONLY valid JSON. No markdown. No extra text.\n" +
                "Keep arrays short: max 4 strengths, 4 issues, 4 fixes, 5 headlines.\n" +
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
                        "maxOutputTokens", 1800,
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

        // If Gemini sends junk/truncated JSON, we FAIL instead of saving garbage
        String cleanJson = extractCleanJsonOrThrow(aiRaw);

        AdCritique saved = repo.save(new AdCritique(
                StringUtils.hasText(originalFilename) ? originalFilename : "upload",
                contentType,
                b64,
                cleanJson
        ));

        return saved;
    }

    private String extractCleanJsonOrThrow(String aiRaw) {
        try {
            JsonNode root = mapper.readTree(aiRaw);

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() == 0) {
                throw new RuntimeException("Gemini: no candidates in response");
            }

            JsonNode parts = candidates.path(0).path("content").path("parts");
            if (!parts.isArray() || parts.size() == 0) {
                throw new RuntimeException("Gemini: no parts in response");
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode p : parts) {
                String t = p.path("text").asText("");
                if (!t.isBlank()) sb.append(t);
            }

            String txt = sb.toString().trim();
            if (txt.isBlank()) {
                // sometimes model returns structured json directly
                // fallback: try content itself
                txt = candidates.path(0).path("content").toString();
            }

            // remove ``` fences if model adds them
            txt = txt.replaceAll("(?s)^```json\\s*", "")
                     .replaceAll("(?s)^```\\s*", "")
                     .replaceAll("(?s)```\\s*$", "")
                     .trim();

            // validate final json
            mapper.readTree(txt);

            // extra safety: must look like critique schema
            JsonNode critique = mapper.readTree(txt);
            if (critique.path("overallScore").isMissingNode()) {
                throw new RuntimeException("Gemini returned JSON but not the expected critique schema");
            }

            return txt;

        } catch (Exception ex) {
            // DO NOT save aiRaw; it breaks UI
            throw new RuntimeException("Invalid / truncated Gemini JSON. " +
                    "Increase tokens or shorten prompt. Raw: " + aiRaw, ex);
        }
    }
}
