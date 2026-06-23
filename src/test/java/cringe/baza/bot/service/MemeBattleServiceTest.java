package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeBattleVoteRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeBattleServiceTest {

    @Mock
    private TelegramBot bot;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private MemeBattleVoteRepository memeBattleVoteRepository;

    @Mock
    private MemeRatingRepository memeRatingRepository;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private MemeBattleService memeBattleService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memeBattleService, "battleDurationMinutes", 10);
        ReflectionTestUtils.setField(memeBattleService, "defaultElo", 1000);
    }

    @Test
    void registerVote_Success() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setStatus("ACTIVE");
        battle.setVotesA(0);
        battle.setVotesB(0);

        when(memeBattleRepository.findById(100L)).thenReturn(Optional.of(battle));
        when(memeBattleVoteRepository.existsByBattleIdAndUserId(100L, 999L)).thenReturn(false);

        boolean result = memeBattleService.registerVote(100L, 999L, "A");

        assertTrue(result);
        assertEquals(1, battle.getVotesA());
        assertEquals(0, battle.getVotesB());
        verify(memeBattleVoteRepository).save(any());
        verify(memeBattleRepository).save(battle);
    }

    @Test
    void registerVote_AlreadyVoted() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setStatus("ACTIVE");

        when(memeBattleRepository.findById(100L)).thenReturn(Optional.of(battle));
        when(memeBattleVoteRepository.existsByBattleIdAndUserId(100L, 999L)).thenReturn(true);

        boolean result = memeBattleService.registerVote(100L, 999L, "A");

        assertFalse(result);
        verify(memeBattleVoteRepository, never()).save(any());
        verify(memeBattleRepository, never()).save(any());
    }

    @Test
    void completeBattle_MemeAWins_EloAndRewardsCalculation() {
        MemeBattle battle = new MemeBattle();
        battle.setId(50L);
        battle.setStatus("ACTIVE");
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(10);
        battle.setVotesB(5);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        MemeModeration memeA = new MemeModeration(
                "meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration(
                "meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));

        MemeRating ratingA = new MemeRating("meme-A", 1000, 0, 0, null);
        MemeRating ratingB = new MemeRating("meme-B", 1000, 0, 0, null);

        when(memeRatingRepository.findById("meme-A")).thenReturn(Optional.of(ratingA));
        when(memeRatingRepository.findById("meme-B")).thenReturn(Optional.of(ratingB));

        TelegramUser authorA = new TelegramUser();
        authorA.setId(111L);
        authorA.setPoints(0);
        authorA.setBattleWins(0);

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(authorA));

        // Вызов метода
        memeBattleService.completeBattle(battle);

        // Проверяем статус баттла и победителя
        assertEquals("COMPLETED", battle.getStatus());
        assertEquals("meme-A", battle.getWinnerMemeId());
        verify(memeBattleRepository).save(battle);

        // Проверяем расчет Elo
        // Expected outcome for both (equal Elo: 1000):
        // expectedA = 1.0 / (1.0 + 10^0) = 0.5
        // expectedB = 0.5
        // scoreA = 1.0, scoreB = 0.0
        // newEloA = 1000 + 32 * (1.0 - 0.5) = 1016
        // newEloB = 1000 + 32 * (0.0 - 0.5) = 984

        ArgumentCaptor<MemeRating> ratingCaptor = ArgumentCaptor.forClass(MemeRating.class);
        verify(memeRatingRepository, times(2)).save(ratingCaptor.capture());

        MemeRating savedRatingA = ratingCaptor.getAllValues().stream()
                .filter(r -> "meme-A".equals(r.getMemeId()))
                .findFirst()
                .orElseThrow();
        MemeRating savedRatingB = ratingCaptor.getAllValues().stream()
                .filter(r -> "meme-B".equals(r.getMemeId()))
                .findFirst()
                .orElseThrow();

        assertEquals(1016, savedRatingA.getEloRating());
        assertEquals(1, savedRatingA.getWins());
        assertEquals(0, savedRatingA.getLosses());

        assertEquals(984, savedRatingB.getEloRating());
        assertEquals(0, savedRatingB.getWins());
        assertEquals(1, savedRatingB.getLosses());

        // Проверяем начисление очков автору мема A
        verify(telegramUserRepository).save(authorA);
        assertEquals(10, authorA.getPoints());
        assertEquals(1, authorA.getBattleWins());
    }

    @Test
    void completeBattle_Duel_ChallengerWins_EloAndStakesRewards() {
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

        MemeModeration memeA = new MemeModeration(
                "meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration(
                "meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));

        MemeRating ratingA = new MemeRating("meme-A", 1000, 0, 0, null);
        MemeRating ratingB = new MemeRating("meme-B", 1000, 0, 0, null);

        when(memeRatingRepository.findById("meme-A")).thenReturn(Optional.of(ratingA));
        when(memeRatingRepository.findById("meme-B")).thenReturn(Optional.of(ratingB));

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        assertEquals("meme-A", battle.getWinnerMemeId());

        assertEquals(210, challenger.getPoints());
        assertEquals(1, challenger.getBattleWins());
        assertEquals(100, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }

    @Test
    void completeBattle_Duel_Draw_StakesRefunded() {
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

        MemeModeration memeA = new MemeModeration(
                "meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration(
                "meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));

        MemeRating ratingA = new MemeRating("meme-A", 1000, 0, 0, null);
        MemeRating ratingB = new MemeRating("meme-B", 1000, 0, 0, null);

        when(memeRatingRepository.findById("meme-A")).thenReturn(Optional.of(ratingA));
        when(memeRatingRepository.findById("meme-B")).thenReturn(Optional.of(ratingB));

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        assertNull(battle.getWinnerMemeId());

        assertEquals(150, challenger.getPoints());
        assertEquals(150, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }
}
