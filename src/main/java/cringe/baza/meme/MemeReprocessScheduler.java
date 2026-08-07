package cringe.baza.meme;

import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeReprocessScheduler {

    private final MemeModerationRepository moderationRepository;
    private final MemeAiProducer aiProducer;

    @Value("${app.ai.reprocess.minutes-threshold:5}")
    private int minutesThreshold;

    @Value("${app.ai.queue.retry-max-delay-ms:3600000}")
    private long retryMaxDelayMs;

    @Value("${app.ai.processing.stuck-threshold-minutes:120}")
    private int stuckThresholdMinutes;

    @Scheduled(fixedDelayString = "${app.ai.reprocess.interval-ms:300000}")
    @Transactional
    public void reenqueuePendingMemes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(minutesThreshold);
        LocalDateTime enqueueThreshold = now.minus(Duration.ofMillis(retryMaxDelayMs * 2));

        List<String> pendingIds = moderationRepository.findPendingIdsOlderThan(threshold, enqueueThreshold);
        for (String memeId : pendingIds) {
            try {
                aiProducer.enqueueForProcessing(memeId);
            } catch (Exception e) {
                log.warn("Не удалось поставить мем {} в очередь: {}", memeId, e.getMessage());
            }
        }
        if (!pendingIds.isEmpty()) {
            log.info(
                    "Re-enqueueing {} PENDING memes older than {} minutes (not enqueued in last {} ms)",
                    pendingIds.size(),
                    minutesThreshold,
                    retryMaxDelayMs * 2);
        }

        LocalDateTime stuckThreshold = now.minusMinutes(stuckThresholdMinutes);
        List<String> stuckIds = moderationRepository.findStuckProcessingIds(stuckThreshold, enqueueThreshold);
        for (String memeId : stuckIds) {
            try {
                aiProducer.enqueueForProcessing(memeId);
            } catch (Exception e) {
                log.warn("Не удалось поставить зависший мем {} в очередь: {}", memeId, e.getMessage());
            }
        }
        if (!stuckIds.isEmpty()) {
            log.info(
                    "Re-enqueueing {} stuck PROCESSING memes (processing started > {} minutes ago, "
                            + "not enqueued in last {} ms)",
                    stuckIds.size(),
                    stuckThresholdMinutes,
                    retryMaxDelayMs * 2);
        }
    }
}
