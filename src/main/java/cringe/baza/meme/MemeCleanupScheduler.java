package cringe.baza.meme;

import cringe.baza.bot.config.MemeAiQueueConfig;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeCleanupScheduler {

    private final MemeModerationRepository repository;
    private final IdRepository idRepository;
    private final TelegramService telegramService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.moderation.quarantine-ttl-days:7}")
    private int quarantineTtlDays;

    @Value("${app.ai.give-up-hours:48}")
    private int giveUpHours;

    @Scheduled(cron = "${app.moderation.cleanup-cron:0 0 2 * * ?}")
    public void cleanExpiredMemes() {
        log.info("Starting scheduled cleanup of expired memes...");
        cleanExpiredQuarantineMemes();
        giveUpAbandonedPendingMemes();
        giveUpAbandonedProcessingMemes();
        log.info("Scheduled cleanup of expired memes finished.");
    }

    private void cleanExpiredQuarantineMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(quarantineTtlDays);
        List<MemeModeration> expired =
                repository.findByStatusAndCreatedAtBefore(ModerationStatus.QUARANTINED, threshold);

        if (expired.isEmpty()) {
            log.info("No expired quarantine memes found.");
            return;
        }

        log.info("Found {} expired quarantine memes to delete.", expired.size());
        for (MemeModeration meme : expired) {
            deleteMeme(meme);
        }
    }

    private void giveUpAbandonedPendingMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(giveUpHours);
        List<MemeModeration> abandoned = repository.findByStatusAndCreatedAtBefore(ModerationStatus.PENDING, threshold);

        if (abandoned.isEmpty()) {
            log.info("No abandoned pending memes to give up.");
            return;
        }

        log.info("Found {} abandoned pending memes older than {} hours to give up.", abandoned.size(), giveUpHours);
        for (MemeModeration meme : abandoned) {
            sendToDlq(meme.getId());
            notifyGiveUp(meme);
            deleteMeme(meme);
        }
    }

    private void giveUpAbandonedProcessingMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(giveUpHours);
        List<MemeModeration> abandoned =
                repository.findByStatusAndProcessingStartedAtBefore(ModerationStatus.PROCESSING, threshold);

        if (abandoned.isEmpty()) {
            log.info("No abandoned processing memes to give up.");
            return;
        }

        log.info("Found {} abandoned processing memes (stuck > {} hours) to give up.", abandoned.size(), giveUpHours);
        for (MemeModeration meme : abandoned) {
            sendToDlq(meme.getId());
            notifyGiveUp(meme);
            deleteMeme(meme);
        }
    }

    private void notifyGiveUp(MemeModeration meme) {
        if (meme.getOwnerId() == null) {
            return;
        }
        try {
            telegramService.sendMessageWithMarkdown(
                    meme.getOwnerId(),
                    "*Не удалось обработать мем.*\n\nМы пытались обработать ваш мем в течение "
                            + giveUpHours
                            + " часов, но AI сейчас недоступен. Мем удалён.\nПопробуйте загрузить его снова позже.");
        } catch (Exception e) {
            log.warn(
                    "Не удалось уведомить пользователя {} об удалении мема {}: {}",
                    meme.getOwnerId(),
                    meme.getId(),
                    e.getMessage());
        }
    }

    private void deleteMeme(MemeModeration meme) {
        try {
            idRepository.delete(meme.getId());
            log.info("Successfully deleted expired meme: {} (status={})", meme.getId(), meme.getStatus());
        } catch (Exception e) {
            log.error("Failed to delete expired meme {}: {}", meme.getId(), e.getMessage());
        }
    }

    private void sendToDlq(String memeId) {
        try {
            rabbitTemplate.convertAndSend(MemeAiQueueConfig.AI_DLX_EXCHANGE, MemeAiQueueConfig.AI_PROCESS_DLQ, memeId);
        } catch (Exception e) {
            log.warn("Не удалось отправить мем {} в DLQ: {}", memeId, e.getMessage());
        }
    }
}
