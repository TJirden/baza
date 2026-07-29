package cringe.baza.meme;

import cringe.baza.repository.jpa.MemeModerationRepository;
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

    @Scheduled(fixedDelayString = "${app.ai.reprocess.interval-ms:300000}")
    public void reenqueuePendingMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesThreshold);
        List<String> pendingIds = moderationRepository.findPendingIdsOlderThan(threshold);

        if (pendingIds.isEmpty()) {
            return;
        }

        log.info("Re-enqueueing {} PENDING memes older than {} minutes", pendingIds.size(), minutesThreshold);
        for (String memeId : pendingIds) {
            try {
                aiProducer.enqueueForProcessing(memeId);
            } catch (Exception e) {
                log.warn("Не удалось поставить мем {} в очередь: {}", memeId, e.getMessage());
            }
        }
    }
}
