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

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeReprocessScheduler {

    private final MemeModerationRepository moderationRepository;
    private final MemeAiProducer aiProducer;

    @Value("${app.ai.reprocess.minutes-threshold:5}")
    private int minutesThreshold;

    @Value("${app.ai.reprocess.interval-ms:300000}")
    private long intervalMs;

    @Scheduled(fixedDelayString = "${app.ai.reprocess.interval-ms:300000}")
    public void reenqueuePendingMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesThreshold);
        LocalDateTime enqueueThreshold = LocalDateTime.now().minus(Duration.ofMillis(intervalMs * 2));
        List<String> pendingIds = moderationRepository.findPendingIdsOlderThan(threshold, enqueueThreshold);

        if (pendingIds.isEmpty()) {
            return;
        }

        log.info(
                "Re-enqueueing {} PENDING memes older than {} minutes (not enqueued in last {} ms)",
                pendingIds.size(),
                minutesThreshold,
                intervalMs * 2);
        for (String memeId : pendingIds) {
            try {
                aiProducer.enqueueForProcessing(memeId);
            } catch (Exception e) {
                log.warn("Не удалось поставить мем {} в очередь: {}", memeId, e.getMessage());
            }
        }
    }
}
