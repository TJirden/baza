package cringe.baza.battle;

import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeBattle;
import cringe.baza.repository.jpa.MemeBattleRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeBattleSchedulerTest {

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private MemeBattleService memeBattleService;

    @Mock
    private MemeDuelLifecycleService memeDuelLifecycleService;

    @InjectMocks
    private MemeBattleScheduler scheduler;

    @Test
    void checkAndCompleteExpiredBattles_NoActiveBattles() {
        when(memeBattleRepository.findByStatus("ACTIVE")).thenReturn(List.of());

        scheduler.checkAndCompleteExpiredBattles();

        verifyNoInteractions(memeBattleService);
    }

    @Test
    void checkAndCompleteExpiredBattles_CompletedAndActive() {
        MemeBattle expired = new MemeBattle();
        expired.setId(1L);
        expired.setEndTime(LocalDateTime.now().minusMinutes(5));

        MemeBattle notExpired = new MemeBattle();
        notExpired.setId(2L);
        notExpired.setEndTime(LocalDateTime.now().plusMinutes(5));

        when(memeBattleRepository.findByStatus("ACTIVE")).thenReturn(List.of(expired, notExpired));

        scheduler.checkAndCompleteExpiredBattles();

        verify(memeBattleService).completeBattle(expired);
        verify(memeBattleService, never()).completeBattle(notExpired);
    }

    @Test
    void checkAndCompleteExpiredBattles_ThrowsException() {
        MemeBattle expired = new MemeBattle();
        expired.setId(1L);
        expired.setEndTime(LocalDateTime.now().minusMinutes(5));

        when(memeBattleRepository.findByStatus("ACTIVE")).thenReturn(List.of(expired));
        doThrow(new RuntimeException("Oops")).when(memeBattleService).completeBattle(expired);

        scheduler.checkAndCompleteExpiredBattles();

        verify(memeBattleService).completeBattle(expired);
    }

    @Test
    void cancelExpiredDuels_ExpiredAndNotExpired() {
        MemeBattle expiredPending = new MemeBattle();
        expiredPending.setId(1L);
        expiredPending.setStartTime(LocalDateTime.now().minusMinutes(15));

        MemeBattle activePending = new MemeBattle();
        activePending.setId(2L);
        activePending.setStartTime(LocalDateTime.now().minusMinutes(5));

        MemeBattle expiredSelection = new MemeBattle();
        expiredSelection.setId(3L);
        expiredSelection.setStartTime(LocalDateTime.now().minusMinutes(15));

        MemeBattle activeSelection = new MemeBattle();
        activeSelection.setId(4L);
        activeSelection.setStartTime(LocalDateTime.now().minusMinutes(5));

        when(memeBattleRepository.findByStatus("PENDING")).thenReturn(List.of(expiredPending, activePending));
        when(memeBattleRepository.findByStatus("MEME_SELECTION"))
                .thenReturn(List.of(expiredSelection, activeSelection));

        scheduler.cancelExpiredDuels();

        verify(memeDuelLifecycleService).cancelPendingDuel(expiredPending, "Время на принятие вызова истекло.");
        verify(memeDuelLifecycleService, never()).cancelPendingDuel(activePending, "Время на принятие вызова истекло.");

        verify(memeDuelLifecycleService).cancelPendingDuel(expiredSelection, "Время на выбор мемов истекло.");
        verify(memeDuelLifecycleService, never()).cancelPendingDuel(activeSelection, "Время на выбор мемов истекло.");
    }

    @Test
    void cancelExpiredDuels_ThrowsException() {
        MemeBattle expiredPending = new MemeBattle();
        expiredPending.setId(1L);
        expiredPending.setStartTime(LocalDateTime.now().minusMinutes(15));

        MemeBattle expiredSelection = new MemeBattle();
        expiredSelection.setId(3L);
        expiredSelection.setStartTime(LocalDateTime.now().minusMinutes(15));

        when(memeBattleRepository.findByStatus("PENDING")).thenReturn(List.of(expiredPending));
        when(memeBattleRepository.findByStatus("MEME_SELECTION")).thenReturn(List.of(expiredSelection));

        doThrow(new RuntimeException("Oops")).when(memeDuelLifecycleService).cancelPendingDuel(any(), anyString());

        scheduler.cancelExpiredDuels();

        verify(memeDuelLifecycleService).cancelPendingDuel(expiredPending, "Время на принятие вызова истекло.");
        verify(memeDuelLifecycleService).cancelPendingDuel(expiredSelection, "Время на выбор мемов истекло.");
    }
}
