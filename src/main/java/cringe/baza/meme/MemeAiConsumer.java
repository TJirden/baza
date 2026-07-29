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
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
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

    @Value("${app.ai.queue.max-retries:5}")
    private int maxRetries;

    @Value("${app.ai.queue.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.ai.queue.retry-multiplier:2.0}")
    private double retryMultiplier;

    @RabbitListener(queues = MemeAiQueueConfig.AI_PROCESS_QUEUE, concurrency = "${app.ai.queue.concurrency:4}")
    public void processMeme(String memeId, @Header(name = "x-retry-count", required = false) Integer retryCount) {
        int attempt = retryCount == null ? 0 : retryCount;
        log.info("Получен мем {} из очереди (попытка {})", memeId, attempt + 1);

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
                publishRetryOrDlq(memeId, attempt, "download failed: " + e.getMessage());
                return;
            }
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Пустое изображение для мема {}, пропускаем", memeId);
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
            publishRetryOrDlq(memeId, attempt, "unexpected: " + e.getMessage());
            return;
        }

        if (result == MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE) {
            publishRetryOrDlq(memeId, attempt, "AI unavailable");
            return;
        }

        imageBytesCache.invalidate(memeId);
        log.info("AI-обработка мема {} завершена: {}", memeId, result);
    }

    private void publishRetryOrDlq(String memeId, int attempt, String reason) {
        if (attempt + 1 >= maxRetries) {
            log.error("Мем {} превысил лимит попыток ({}), отправляем в DLQ: {}", memeId, maxRetries, reason);
            throw new AmqpRejectAndDontRequeueException("Max retries exceeded for meme " + memeId + ": " + reason);
        }
        int nextAttempt = attempt + 1;
        long delay = (long) (retryDelayMs * Math.pow(retryMultiplier, attempt));
        log.info(
                "Повторная постановка мема {} в очередь (попытка {}, задержка {} мс): {}",
                memeId,
                nextAttempt,
                delay,
                reason);
        rabbitTemplate.convertAndSend(
                MemeAiQueueConfig.AI_DLX_EXCHANGE, MemeAiQueueConfig.AI_PROCESS_RETRY_QUEUE, memeId, message -> {
                    message.getMessageProperties().setHeader("x-retry-count", nextAttempt);
                    message.getMessageProperties().setExpiration(String.valueOf(delay));
                    return message;
                });
    }
}
