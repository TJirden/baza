package cringe.baza.presentation.dto;

import java.time.LocalDateTime;

public record MemeResponse(
        String id,
        String filename,
        String description,
        String imageUrl,
        LocalDateTime createdAt,
        Long fileSize
) {}