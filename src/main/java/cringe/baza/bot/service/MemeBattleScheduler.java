package cringe.baza.bot.service;

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
}
