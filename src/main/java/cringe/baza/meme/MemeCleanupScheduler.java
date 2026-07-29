package cringe.baza.meme;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.ModerationStatus;
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
public class MemeCleanupScheduler {

    private final MemeModerationRepository repository;
    private final IdRepository idRepository;

    @Value("${app.moderation.quarantine-ttl-days:7}")
    private int quarantineTtlDays;

    @Value("${app.moderation.pending-ttl-days:30}")
    private int pendingTtlDays;

    @Scheduled(cron = "${app.moderation.cleanup-cron:0 0 2 * * ?}")
    public void cleanExpiredMemes() {
        log.info("Starting scheduled cleanup of expired memes...");
        cleanExpiredQuarantineMemes();
        cleanExpiredPendingMemes();
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

    private void cleanExpiredPendingMemes() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(pendingTtlDays);
        List<MemeModeration> expired = repository.findByStatusAndCreatedAtBefore(ModerationStatus.PENDING, threshold);

        if (expired.isEmpty()) {
            log.info("No expired pending memes found.");
            return;
        }

        log.info("Found {} expired pending memes to delete.", expired.size());
        for (MemeModeration meme : expired) {
            deleteMeme(meme);
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
}
