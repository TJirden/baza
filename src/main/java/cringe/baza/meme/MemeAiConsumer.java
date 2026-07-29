package cringe.baza.meme;

import cringe.baza.bot.config.MemeAiQueueConfig;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemeAiConsumer {

    private final MemeAiProcessingService aiProcessingService;
    private final IdRepository idRepository;
    private final TelegramFileService fileService;

    @Value("${app.ai.queue.concurrency:4}")
    private int concurrency;

    @RabbitListener(queues = MemeAiQueueConfig.AI_PROCESS_QUEUE, concurrency = "${app.ai.queue.concurrency:4}")
    public void processMeme(String memeId) {
        log.info("Получен мем {} из очереди на AI-обработку", memeId);

        Optional<MemeModeration> moderationOpt = idRepository.findModerationById(memeId);
        if (moderationOpt.isEmpty()) {
            log.warn("Мем {} не найден, пропускаем", memeId);
            return;
        }

        MemeModeration moderation = moderationOpt.get();
        if (moderation.getStatus() != ModerationStatus.PENDING) {
            log.info("Мем {} уже обработан (статус={}), пропускаем", memeId, moderation.getStatus());
            return;
        }

        byte[] imageBytes;
        try {
            imageBytes = fileService.downloadFileBytes(moderation.getFileId());
        } catch (Exception e) {
            log.error("Не удалось скачать изображение для мема {}: {}", memeId, e.getMessage());
            return;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            log.error("Пустое изображение для мема {}, пропускаем", memeId);
            return;
        }

        long userId = moderation.getOwnerId() != null ? moderation.getOwnerId() : 0L;
        MemeVisibility visibility = moderation.getVisibility();
        String groupIdsStr = moderation.getGroupIds();
        String userDescription = extractUserDescription(moderation.getDescription());

        MemeAiProcessingService.AiProcessingResult result = aiProcessingService.processAiAndFinalize(
                memeId, imageBytes, userDescription, userId, visibility, groupIdsStr);

        log.info("AI-обработка мема {} завершена: {}", memeId, result);
    }

    private String extractUserDescription(String storedDescription) {
        if (storedDescription == null || storedDescription.isBlank()) {
            return null;
        }
        int tagsIndex = storedDescription.indexOf("\n\n[ИИ-Теги]:");
        if (tagsIndex > 0) {
            return storedDescription.substring(0, tagsIndex);
        }
        return storedDescription;
    }
}
