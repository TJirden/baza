package cringe.baza.meme;

import cringe.baza.bot.service.TelegramService;
import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeAiProcessingService {

    private final MemeAnalyzerService memeAnalyzerService;
    private final IdRepository idRepository;
    private final TelegramService telegramService;

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
            if (updateToQuarantined(memeId, reason, finalDescription, ocrText)) {
                notifyUserQuarantined(userId, memeId, reason);
            }
            return AiProcessingResult.QUARANTINED_CENSORSHIP;
        }

        List<Long> groupIds = parseGroupIds(groupIdsStr);
        boolean promoted = idRepository.promoteToApproved(
                memeId, new Meme(memeId, finalDescription, ocrText, null, userId, visibility, groupIds));
        if (!promoted) {
            log.warn("Мем {} уже не PENDING, промоут пропущен", memeId);
            return AiProcessingResult.APPROVED;
        }
        log.info("Мем {} успешно одобрен после AI-обработки", memeId);
        notifyUserApproved(userId, memeId);
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

    private void notifyUserApproved(long userId, String memeId) {
        try {
            telegramService.sendMessageWithMarkdown(
                    userId,
                    "*Ваш мем успешно обработан и одобрен!*\n\n*ID мема:* `" + memeId
                            + "`\nТеперь он доступен в поиске.");
        } catch (Exception e) {
            log.warn("Не удалось уведомить пользователя {} об одобрении мема: {}", userId, e.getMessage());
        }
    }

    private void notifyUserQuarantined(long userId, String memeId, String reason) {
        try {
            String text = "*Мем помещен в карантин.*\n\n*ID мема:* `" + memeId + "`";
            if (reason != null && !reason.isBlank()) {
                text += "\n*Причина:* " + reason;
            }
            text += "\nМем будет доступен только после ручного одобрения модератором.";
            telegramService.sendMessageWithMarkdown(userId, text);
        } catch (Exception e) {
            log.warn("Не удалось уведомить пользователя {} о карантине мема: {}", userId, e.getMessage());
        }
    }
}
