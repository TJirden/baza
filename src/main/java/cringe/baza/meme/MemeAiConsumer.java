package cringe.baza.meme;

import com.github.benmanes.caffeine.cache.Cache;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemeAiConsumer {

    private final MemeAiProcessingService aiProcessingService;
    private final IdRepository idRepository;
    private final TelegramFileService fileService;
    private final RabbitTemplate rabbitTemplate;
    private final Cache<String, byte[]> imageBytesCache;

    @Value("${app.ai.queue.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.ai.queue.retry-multiplier:2.0}")
    private double retryMultiplier;

    @Value("${app.ai.queue.retry-max-delay-ms:3600000}")
    private long retryMaxDelayMs;

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

        byte[] imageBytes = imageBytesCache.getIfPresent(memeId);
        if (imageBytes == null || imageBytes.length == 0) {
            try {
                imageBytes = fileService.downloadFileBytes(moderation.getFileId());
            } catch (Exception e) {
                log.error("Не удалось скачать изображение для мема {}: {}", memeId, e.getMessage());
                publishRetry(memeId, "download failed: " + e.getMessage());
                return;
            }
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Пустое изображение для мема {}, ставим в retry", memeId);
                publishRetry(memeId, "пустое изображение из Telegram");
                return;
            }
            imageBytesCache.put(memeId, imageBytes);
        }

        long userId = moderation.getOwnerId() != null ? moderation.getOwnerId() : 0L;
        MemeVisibility visibility = moderation.getVisibility();
        String groupIdsStr = moderation.getGroupIds();
        String userDescription = moderation.getDescription();

        MemeAiProcessingService.AiProcessingResult result;
        try {
            result = aiProcessingService.processAiAndFinalize(
                    memeId, imageBytes, userDescription, userId, visibility, groupIdsStr);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при AI-обработке мема {}: {}", memeId, e.getMessage(), e);
            publishRetry(memeId, "unexpected: " + e.getMessage());
            return;
        }

        if (result == MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE) {
            publishRetry(memeId, "AI unavailable");
            return;
        }

        imageBytesCache.invalidate(memeId);
        log.info("AI-обработка мема {} завершена: {}", memeId, result);
    }

    private void publishRetry(String memeId, String reason) {
        int updated = idRepository.incrementRetryCount(memeId);
        if (updated == 0) {
            log.warn("Мем {} уже не PENDING, повторная постановка в очередь отменена", memeId);
            return;
        }

        Optional<MemeModeration> fresh = idRepository.findModerationById(memeId);
        int attempt = fresh.map(MemeModeration::getRetryCount).orElse(0);
        long rawDelay = (long) (retryDelayMs * Math.pow(retryMultiplier, Math.max(0, attempt - 1)));
        long delay = Math.min(rawDelay, retryMaxDelayMs);
        log.info(
                "Повторная постановка мема {} в очередь (попытка {}, задержка {} мс): {}",
                memeId,
                attempt,
                delay,
                reason);
        rabbitTemplate.convertAndSend(
                MemeAiQueueConfig.AI_DLX_EXCHANGE, MemeAiQueueConfig.AI_PROCESS_RETRY_QUEUE, memeId, message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delay));
                    return message;
                });
    }
}
