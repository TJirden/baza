package cringe.baza.meme;

import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeAiProcessingService {

    private final MemeAnalyzerService memeAnalyzerService;
    private final IdRepository idRepository;
    private final TelegramService telegramService;
    private final CircuitBreaker aiCircuitBreaker;

    @Value("${app.dedup.text-threshold}")
    private double textDedupThreshold;

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
            analysis = CircuitBreaker.decorateSupplier(aiCircuitBreaker, () -> memeAnalyzerService.analyze(imageBytes))
                    .get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker открыт, AI-обработка мема {} отложена", memeId);
            return AiProcessingResult.AI_UNAVAILABLE;
        } catch (AiUnavailableException e) {
            log.warn("AI недоступен для мема {}: {}", memeId, e.getMessage());
            return AiProcessingResult.AI_UNAVAILABLE;
        }

        String finalDescription = buildFinalDescription(userDescription, analysis.description());
        String ocrText = analysis.ocrText();

        if (!analysis.safe()) {
            log.warn("Мем {} не прошел цензуру ИИ. Причина: {}", memeId, analysis.censorshipReason());
            updateToQuarantined(memeId, "ИИ-цензура: " + analysis.censorshipReason(), finalDescription, ocrText);
            notifyUserQuarantined(userId, memeId, "ИИ-цензура: " + analysis.censorshipReason());
            return AiProcessingResult.QUARANTINED_CENSORSHIP;
        }

        Optional<String> duplicateIdOpt;
        try {
            duplicateIdOpt = idRepository.findDuplicateMemeId(finalDescription, textDedupThreshold);
        } catch (Exception e) {
            log.warn("Ошибка при текстовой проверке дубликатов мема {}: {}", memeId, e.getMessage());
            updateToQuarantined(
                    memeId, "Ошибка текстовой проверки дубликатов: " + e.getMessage(), finalDescription, ocrText);
            notifyUserQuarantined(userId, memeId, null);
            return AiProcessingResult.QUARANTINED_DUPLICATE;
        }

        if (duplicateIdOpt.isPresent()) {
            String duplicateId = duplicateIdOpt.get();
            log.warn("Обнаружен дубликат мема {}. Оригинал: {}", memeId, duplicateId);
            updateToQuarantined(memeId, "Дубликат мема: " + duplicateId, finalDescription, ocrText);
            notifyUserQuarantined(userId, memeId, null);
            return AiProcessingResult.QUARANTINED_DUPLICATE;
        }

        List<Long> groupIds = parseGroupIds(groupIdsStr);
        idRepository.promoteToApproved(
                memeId, new Meme(memeId, finalDescription, ocrText, null, userId, visibility, groupIds));
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

    private void updateToQuarantined(String memeId, String reason, String description, String ocrText) {
        Optional<MemeModeration> moderationOpt = idRepository.findModerationById(memeId);
        if (moderationOpt.isEmpty()) {
            log.error("Мем {} не найден при обновлении статуса на QUARANTINED", memeId);
            return;
        }
        MemeModeration moderation = moderationOpt.get();
        moderation.setStatus(ModerationStatus.QUARANTINED);
        moderation.setModerationReason(reason);
        moderation.setDescription(description);
        moderation.setOcrText(ocrText);
        idRepository.saveQuarantined(moderation, OptionalLong.empty());
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
