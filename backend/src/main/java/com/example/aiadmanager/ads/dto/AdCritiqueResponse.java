package com.example.aiadmanager.ads.dto;

import java.time.Instant;

public record AdCritiqueResponse(
        Long id,
        String originalFilename,
        String contentType,
        String imageBase64,
        String aiJson,
        Instant createdAt
) {}
