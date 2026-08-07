package cringe.baza.meme;

import com.github.benmanes.caffeine.cache.Cache;
import cringe.baza.bot.config.MemeAiQueueConfig;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
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
    private final TelegramService telegramService;
    private final RabbitTemplate rabbitTemplate;
    private final Cache<String, byte[]> imageBytesCache;

    @Value("${app.ai.queue.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.ai.queue.retry-multiplier:2.0}")
    private double retryMultiplier;

    @Value("${app.ai.queue.retry-max-delay-ms:3600000}")
    private long retryMaxDelayMs;

    @Value("${app.ai.queue.max-retries:10}")
    private int maxRetries;

    @RabbitListener(queues = MemeAiQueueConfig.AI_PROCESS_QUEUE, concurrency = "${app.ai.queue.concurrency:4}")
    public void processMeme(String memeId) {
        log.info("Получен мем {} из очереди на AI-обработку", memeId);

        Optional<MemeModeration> moderationOpt = idRepository.findModerationById(memeId);
        if (moderationOpt.isEmpty()) {
            log.warn("Мем {} не найден, пропускаем", memeId);
            return;
        }

        MemeModeration moderation = moderationOpt.get();
        if (moderation.getStatus() == ModerationStatus.APPROVED
                || moderation.getStatus() == ModerationStatus.QUARANTINED) {
            log.info("Мем {} уже обработан (статус={}), пропускаем", memeId, moderation.getStatus());
            return;
        }

        if (!idRepository.claimForProcessing(memeId)) {
            log.info("Мем {} уже забран другим воркером, пропускаем", memeId);
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
        } catch (TransientProcessingException | AiUnavailableException e) {
            log.warn("Транзитная ошибка при AI-обработке мема {}: {}", memeId, e.getMessage());
            publishRetry(memeId, "transient: " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Неустранимая ошибка при AI-обработке мема {}: {}", memeId, e.getMessage(), e);
            handlePermanentFailure(memeId, moderation, "permanent: " + e.getMessage());
            return;
        }

        if (result == MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE) {
            publishRetry(memeId, "AI unavailable");
            return;
        }

        imageBytesCache.invalidate(memeId);
        notifyUserOfResult(memeId, userId, result);
        log.info("AI-обработка мема {} завершена: {}", memeId, result);
    }

    private void notifyUserOfResult(String memeId, long userId, MemeAiProcessingService.AiProcessingResult result) {
        try {
            switch (result) {
                case APPROVED ->
                    telegramService.sendMessageWithMarkdown(
                            userId,
                            "*Ваш мем успешно обработан и одобрен!*\n\n*ID мема:* `" + memeId
                                    + "`\nТеперь он доступен в поиске.");
                case QUARANTINED_CENSORSHIP, QUARANTINED_DUPLICATE ->
                    telegramService.sendMessageWithMarkdown(
                            userId,
                            "*Мем помещен в карантин.*\n\n*ID мема:* `" + memeId
                                    + "`\nМем будет доступен только после ручного одобрения модератором.");
                case AI_UNAVAILABLE -> {}
            }
        } catch (Exception e) {
            log.warn("Не удалось уведомить пользователя {} о результате мема {}: {}", userId, memeId, e.getMessage());
        }
    }

    private void handlePermanentFailure(String memeId, MemeModeration moderation, String reason) {
        log.error("Мем {} отправлен в DLQ (permanent failure): {}", memeId, reason);
        rabbitTemplate.convertAndSend(MemeAiQueueConfig.AI_DLX_EXCHANGE, MemeAiQueueConfig.AI_PROCESS_DLQ, memeId);
        imageBytesCache.invalidate(memeId);
        try {
            idRepository.delete(memeId);
        } catch (Exception e) {
            log.warn("Не удалось удалить мем {} из БД: {}", memeId, e.getMessage());
        }
        Long ownerId = moderation.getOwnerId();
        if (ownerId != null) {
            try {
                telegramService.sendMessageWithMarkdown(
                        ownerId,
                        "*Не удалось обработать мем.*\n\nПроизошла внутренняя ошибка, мем удалён. "
                                + "Попробуйте загрузить его снова.");
            } catch (Exception e) {
                log.warn("Не удалось уведомить пользователя {} о неудаче мема {}: {}", ownerId, memeId, e.getMessage());
            }
        }
    }

    private void publishRetry(String memeId, String reason) {
        int updated = idRepository.incrementRetryCount(memeId);
        if (updated == 0) {
            log.warn("Мем {} уже не PROCESSING, повторная постановка в очередь отменена", memeId);
            return;
        }

        Optional<MemeModeration> fresh = idRepository.findModerationById(memeId);
        if (fresh.isEmpty()) {
            log.warn("Мем {} не найден после инкремента retry-счётчика, пропускаем", memeId);
            return;
        }

        MemeModeration current = fresh.get();
        int attempt = current.getRetryCount();
        if (attempt > maxRetries) {
            log.warn("Мем {} превысил лимит ретраев ({}/{}), отправляем в DLQ", memeId, attempt, maxRetries);
            handlePermanentFailure(memeId, current, "превышен лимит ретраев: " + attempt + "/" + maxRetries);
            return;
        }

        long rawDelay = (long) (retryDelayMs * Math.pow(retryMultiplier, Math.max(0, attempt - 1)));
        long delay = Math.min(rawDelay, retryMaxDelayMs);
        log.info(
                "Повторная постановка мема {} в очередь (попытка {}, задержка {} мс): {}",
                memeId,
                attempt,
                delay,
                reason);
        rabbitTemplate.convertAndSend(
                MemeAiQueueConfig.AI_RETRY_EXCHANGE, MemeAiQueueConfig.AI_PROCESS_QUEUE, memeId, message -> {
                    message.getMessageProperties().setHeader("x-delay", delay);
                    return message;
                });
    }
}
