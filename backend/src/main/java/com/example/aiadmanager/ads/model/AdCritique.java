package com.example.aiadmanager.ads.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ad_critiques")
public class AdCritique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="original_filename", nullable = false)
    private String originalFilename;

    @Column(name="content_type", nullable = false)
    private String contentType;

    @Column(name="image_base64", columnDefinition = "TEXT", nullable = false)
    private String imageBase64;

    @Column(name="ai_json", columnDefinition = "TEXT", nullable = false)
    private String aiJson;

    @Column(name="created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AdCritique() {}

    public AdCritique(String originalFilename, String contentType, String imageBase64, String aiJson) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.imageBase64 = imageBase64;
        this.aiJson = aiJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public String getImageBase64() { return imageBase64; }
    public String getAiJson() { return aiJson; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public void setAiJson(String aiJson) { this.aiJson = aiJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
