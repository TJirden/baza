package cringe.baza.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeDigestScheduler {

    private final MemeDigestService digestService;

    @Scheduled(cron = "${app.digest.cron:0 0 12 * * ?}")
    public void runDigestJob() {
        log.info("MemeDigestScheduler triggered. Starting job...");
        try {
            digestService.runAllGroupDigests();
            log.info("MemeDigestScheduler job completed successfully.");
        } catch (Exception e) {
            log.error("Error during scheduled meme digest generation: {}", e.getMessage(), e);
        }
    }
}
