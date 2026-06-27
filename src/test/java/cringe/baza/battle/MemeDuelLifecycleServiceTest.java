package cringe.baza.battle;

import cringe.baza.bot.service.TelegramService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeDuelLifecycleServiceTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @InjectMocks
    private MemeDuelLifecycleService memeDuelLifecycleService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memeDuelLifecycleService, "battleDurationMinutes", 10);
    }

    @Test
    void startActiveDuel_MemesNotFound() {
        MemeBattle battle = new MemeBattle();
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.empty());

        memeDuelLifecycleService.startActiveDuel(battle);

        assertEquals("FAILED", battle.getStatus());
        verify(memeBattleRepository).save(battle);
    }

    @Test
    void startActiveDuel_Success() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setTelegramChatId(999L);

        MemeModeration memeA = new MemeModeration("meme-A", "file-A", "desc-A", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-B", "file-B", "desc-B", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(111L);
        challenger.setFirstName("Challenger");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(222L);
        opponent.setFirstName("Opponent");

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));
        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));
        when(telegramService.sendBattleStart(eq(999L), eq("file-A"), anyString(), eq("file-B"), anyString(), anyString(), eq(100L)))
                .thenReturn(777);

        memeDuelLifecycleService.startActiveDuel(battle);

        assertEquals("ACTIVE", battle.getStatus());
        assertEquals(777, battle.getTelegramMessageId());
        verify(memeBattleRepository, times(2)).save(battle);
    }

    @Test
    void startActiveDuel_SendFails() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setTelegramChatId(999L);

        MemeModeration memeA = new MemeModeration("meme-A", "file-A", "desc-A", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-B", "file-B", "desc-B", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(111L);
        challenger.setFirstName("Challenger");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(222L);
        opponent.setFirstName("Opponent");

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));
        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));
        when(telegramService.sendBattleStart(eq(999L), eq("file-A"), anyString(), eq("file-B"), anyString(), anyString(), eq(100L)))
                .thenReturn(null);

        memeDuelLifecycleService.startActiveDuel(battle);

        assertEquals("FAILED", battle.getStatus());
        verify(memeBattleRepository, times(2)).save(battle);
    }

    @Test
    void cancelPendingDuel_Success() {
        MemeBattle duel = new MemeBattle();
        duel.setStatus("MEME_SELECTION");
        duel.setChallengerId(111L);
        duel.setOpponentId(222L);
        duel.setBet(50);
        duel.setTelegramChatId(999L);
        duel.setTelegramMessageId(888);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(111L);
        challenger.setPoints(100);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(222L);
        opponent.setPoints(100);

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.cancelPendingDuel(duel, "Timeout");

        assertEquals("EXPIRED", duel.getStatus());
        assertEquals(150, challenger.getPoints());
        assertEquals(150, opponent.getPoints());
        verify(memeBattleRepository).save(duel);
        verify(telegramService).editMessageTextWithMarkdown(eq(999L), eq(888), anyString());
    }

    @Test
    void completeDuel_ChallengerWins_EloAndStakesRewards() {
        MemeBattle battle = new MemeBattle();
        battle.setId(77L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setBet(50);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(15);
        battle.setVotesB(5);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.completeDuel(battle, "meme-A", 50);

        assertEquals(210, challenger.getPoints());
        assertEquals(1, challenger.getBattleWins());
        assertEquals(100, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }

    @Test
    void completeDuel_OpponentWins() {
        MemeBattle battle = new MemeBattle();
        battle.setId(77L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setBet(50);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(5);
        battle.setVotesB(15);

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.completeDuel(battle, "meme-B", 50);

        assertEquals(100, challenger.getPoints());
        assertEquals(210, opponent.getPoints());
        assertEquals(1, opponent.getBattleWins());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }

    @Test
    void completeDuel_Draw_StakesRefunded() {
        MemeBattle battle = new MemeBattle();
        battle.setId(78L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setBet(50);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(5);
        battle.setVotesB(5);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.completeDuel(battle, null, 50);

        assertEquals(150, challenger.getPoints());
        assertEquals(150, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }
}
