package com.example.aiadmanager.ads.controller;
import com.example.aiadmanager.ads.dto.AdCritiqueResponse;
import com.example.aiadmanager.ads.model.AdCritique;
import com.example.aiadmanager.ads.repo.AdCritiqueRepository;
import com.example.aiadmanager.ads.service.GeminiAdAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/ads")
public class AdAnalysisController {

    private final GeminiAdAnalysisService service;
    private final AdCritiqueRepository repo;

    public AdAnalysisController(GeminiAdAnalysisService service, AdCritiqueRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdCritiqueResponse analyze(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        AdCritique saved = service.analyzeAndSave(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );
        return toDto(saved);
    }

    @GetMapping("/history")
    public List<AdCritiqueResponse> history() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(AdCritique::getCreatedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/history/{id}")
    public AdCritiqueResponse byId(@PathVariable Long id) {
        AdCritique c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
        return toDto(c);
    }

    private AdCritiqueResponse toDto(AdCritique c) {
        return new AdCritiqueResponse(
                c.getId(),
                c.getOriginalFilename(),
                c.getContentType(),
                c.getImageBase64(),
                c.getAiJson(),
                c.getCreatedAt()
        );
    }
}
