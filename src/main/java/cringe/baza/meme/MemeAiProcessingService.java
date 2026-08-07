package cringe.baza.meme;

import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeAiProcessingService {

    private final MemeAnalyzerService memeAnalyzerService;
    private final IdRepository idRepository;

    @Value("${app.dedup.image-phash-threshold}")
    private int phashThreshold;

    public enum AiProcessingResult {
        APPROVED,
        QUARANTINED_CENSORSHIP,
        QUARANTINED_DUPLICATE,
        AI_UNAVAILABLE
    }

    public AiProcessingResult processAiAndFinalize(
            String memeId,
            byte[] imageBytes,
            String userDescription,
            long userId,
            MemeVisibility visibility,
            String groupIdsStr) {
        MemeAnalyzerService.MemeAnalysis analysis;
        try {
            analysis = memeAnalyzerService.analyze(imageBytes);
        } catch (AiUnavailableException e) {
            log.warn("AI недоступен для мема {}: {}", memeId, e.getMessage());
            return AiProcessingResult.AI_UNAVAILABLE;
        }

        String finalDescription = buildFinalDescription(userDescription, analysis.description());
        String ocrText = analysis.ocrText();

        if (!analysis.safe()) {
            log.warn("Мем {} не прошел цензуру ИИ. Причина: {}", memeId, analysis.censorshipReason());
            String reason = "ИИ-цензура: " + analysis.censorshipReason();
            try {
                updateToQuarantined(memeId, reason, finalDescription, ocrText);
            } catch (DataAccessException e) {
                throw new TransientProcessingException("Ошибка БД при карантине мема " + memeId, e);
            }
            return AiProcessingResult.QUARANTINED_CENSORSHIP;
        }

        List<Long> groupIds = parseGroupIds(groupIdsStr);

        Optional<String> duplicateId = idRepository.findApprovedDuplicate(memeId, phashThreshold);
        if (duplicateId.isPresent()) {
            log.warn("Мем {} заблокирован как визуальный дубликат одобренного мема {}", memeId, duplicateId.get());
            String reason = "Визуальный дубликат мема: " + duplicateId.get();
            try {
                updateToQuarantined(memeId, reason, finalDescription, ocrText);
            } catch (DataAccessException e) {
                throw new TransientProcessingException("Ошибка БД при карантине мема (дубликат) " + memeId, e);
            }
            return AiProcessingResult.QUARANTINED_DUPLICATE;
        }

        boolean promoted;
        try {
            promoted = idRepository.promoteToApproved(
                    memeId, new Meme(memeId, finalDescription, ocrText, null, userId, visibility, groupIds));
        } catch (DataAccessException e) {
            throw new TransientProcessingException("Ошибка БД при промоуте мема " + memeId, e);
        }
        if (!promoted) {
            log.warn("Мем {} уже не PENDING/PROCESSING, промоут пропущен", memeId);
            return AiProcessingResult.APPROVED;
        }
        log.info("Мем {} успешно одобрен после AI-обработки", memeId);
        return AiProcessingResult.APPROVED;
    }

    private String buildFinalDescription(String userDescription, String aiDescription) {
        if (userDescription == null || userDescription.isBlank()) {
            return aiDescription;
        }
        if (aiDescription != null && !aiDescription.isBlank() && !"Без описания".equals(aiDescription)) {
            return userDescription + "\n\n[ИИ-Теги]: " + aiDescription;
        }
        return userDescription;
    }

    private boolean updateToQuarantined(String memeId, String reason, String description, String ocrText) {
        boolean updated = idRepository.updateToQuarantinedIfPending(memeId, description, ocrText, reason);
        if (!updated) {
            log.warn("Мем {} уже не PENDING, перевод в QUARANTINED пропущен", memeId);
        }
        return updated;
    }

    private List<Long> parseGroupIds(String groupIdsStr) {
        List<Long> groupIds = new ArrayList<>();
        if (groupIdsStr != null && !groupIdsStr.isBlank()) {
            for (String g : groupIdsStr.split(",")) {
                try {
                    groupIds.add(Long.parseLong(g.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return groupIds;
    }
}
