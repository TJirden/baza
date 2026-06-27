package cringe.baza.battle;

import cringe.baza.domain.MemeBattle;
import cringe.baza.repository.jpa.MemeBattleRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeBattleScheduler {

    private final MemeBattleRepository memeBattleRepository;
    private final MemeBattleService memeBattleService;
    private final MemeDuelLifecycleService memeDuelLifecycleService;

    /**
     * Проверяет активные баттлы раз в минуту и закрывает те, время голосования которых истекло.
     */
    @Scheduled(fixedDelay = 60000)
    public void checkAndCompleteExpiredBattles() {
        log.debug("Checking for expired meme battles...");
        List<MemeBattle> activeBattles = memeBattleRepository.findByStatus("ACTIVE");

        if (activeBattles.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (MemeBattle battle : activeBattles) {
            if (now.isAfter(battle.getEndTime())) {
                try {
                    memeBattleService.completeBattle(battle);
                } catch (Exception e) {
                    log.error("Failed to automatically complete battle {}: {}", battle.getId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Проверяет просроченные дуэли и отменяет их с возвратом ставок, если необходимо.
     */
    @Scheduled(fixedDelay = 60000)
    public void cancelExpiredDuels() {
        log.debug("Checking for expired pending or selection duels...");
        List<MemeBattle> pendingDuels = memeBattleRepository.findByStatus("PENDING");
        List<MemeBattle> selectionDuels = memeBattleRepository.findByStatus("MEME_SELECTION");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        for (MemeBattle duel : pendingDuels) {
            if (duel.getStartTime() != null && duel.getStartTime().isBefore(threshold)) {
                try {
                    memeDuelLifecycleService.cancelPendingDuel(duel, "Время на принятие вызова истекло.");
                } catch (Exception e) {
                    log.error("Failed to cancel pending duel {}: {}", duel.getId(), e.getMessage(), e);
                }
            }
        }

        for (MemeBattle duel : selectionDuels) {
            if (duel.getStartTime() != null && duel.getStartTime().isBefore(threshold)) {
                try {
                    memeDuelLifecycleService.cancelPendingDuel(duel, "Время на выбор мемов истекло.");
                } catch (Exception e) {
                    log.error("Failed to cancel selection duel {}: {}", duel.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
