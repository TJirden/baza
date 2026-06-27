package cringe.baza.meme;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeCleanupScheduler {

    private final MemeModerationRepository repository;
    private final IdRepository idRepository;

    @Scheduled(cron = "${app.moderation.cleanup-cron:0 0 2 * * ?}")
    public void cleanExpiredQuarantineMemes() {
        log.info("Starting scheduled cleanup of expired quarantine memes...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<MemeModeration> expired =
                repository.findByStatusAndCreatedAtBefore(ModerationStatus.QUARANTINED, threshold);

        if (expired.isEmpty()) {
            log.info("No expired quarantine memes found.");
            return;
        }

        log.info("Found {} expired quarantine memes to delete.", expired.size());
        for (MemeModeration meme : expired) {
            try {
                idRepository.delete(meme.getId());
                repository.delete(meme);
                log.info("Successfully deleted expired quarantine meme: {}", meme.getId());
            } catch (Exception e) {
                log.error("Failed to delete expired quarantine meme {}: {}", meme.getId(), e.getMessage());
            }
        }
        log.info("Scheduled cleanup of expired quarantine memes finished.");
    }
}
