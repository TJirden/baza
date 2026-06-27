package cringe.baza.battle;

import cringe.baza.bot.service.TelegramService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeBattleVote;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeBattleServiceTest {

    @Mock
    private TelegramService telegramService;

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

    @Mock
    private MemeDuelLifecycleService memeDuelLifecycleService;

    @InjectMocks
    private MemeBattleService memeBattleService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memeBattleService, "battleDurationMinutes", 10);
        ReflectionTestUtils.setField(memeBattleService, "defaultElo", 1000);
    }

    @Test
    void startBattle_InsufficientMemes() {
        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));

        memeBattleService.startBattle(123L);

        verify(telegramService).sendMessage(eq(123L), contains("Недостаточно публичных мемов"));
        verifyNoInteractions(memeBattleRepository);
    }

    @Test
    void startBattle_SuccessWithVectorSearch() {
        MemeModeration memeA = new MemeModeration("meme-1", "file-1", "desc1", "ocr", 10L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-2", "file-2", "desc2", "ocr", 20L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(memeA, memeB));

        Document doc = new Document("meme-2", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(memeModerationRepository.findById("meme-2")).thenReturn(Optional.of(memeB));

        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(invocation -> {
            MemeBattle b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });
        lenient().when(telegramService.sendBattleStart(anyLong(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(999);

        memeBattleService.startBattle(123L);

        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        assertEquals(999, battleCaptor.getValue().getTelegramMessageId());
    }

    @Test
    void startBattle_VectorSearchThrowsAndFallbackSuccess() {
        MemeModeration memeA = new MemeModeration("meme-1", "file-1", "desc1", "ocr", 10L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-2", "file-2", "desc2", "ocr", 20L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(memeA, memeB));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("Vector store unavailable"));

        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(invocation -> {
            MemeBattle b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });
        lenient().when(telegramService.sendBattleStart(anyLong(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(999);

        memeBattleService.startBattle(123L);

        verify(memeBattleRepository, times(2)).save(any(MemeBattle.class));
    }

    @Test
    void startBattle_FailedToSendVoteCard() {
        MemeModeration memeA = new MemeModeration("meme-1", "file-1", "desc1", "ocr", 10L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-2", "file-2", "desc2", "ocr", 20L, MemeVisibility.PUBLIC, "tags", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(memeA, memeB));

        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(invocation -> {
            MemeBattle b = invocation.getArgument(0);
            b.setId(100L);
            return b;
        });
        lenient().when(telegramService.sendBattleStart(anyLong(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(null);

        memeBattleService.startBattle(123L);

        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        assertEquals("FAILED", battleCaptor.getValue().getStatus());
    }

    @Test
    void registerVote_SuccessA() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setStatus("ACTIVE");
        battle.setVotesA(0);
        battle.setVotesB(0);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setTelegramChatId(123L);
        battle.setTelegramMessageId(456);
        battle.setEndTime(LocalDateTime.now().plusMinutes(10));

        when(memeBattleRepository.findById(100L)).thenReturn(Optional.of(battle));
        when(memeBattleVoteRepository.existsByBattleIdAndUserId(100L, 999L)).thenReturn(false);
        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(new MemeModeration()));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(new MemeModeration()));

        boolean result = memeBattleService.registerVote(100L, 999L, "A");

        assertTrue(result);
        assertEquals(1, battle.getVotesA());
        assertEquals(0, battle.getVotesB());
        verify(memeBattleVoteRepository).save(any(MemeBattleVote.class));
        verify(memeBattleRepository).save(battle);
        verify(telegramService).editBattleVoteCard(eq(123L), eq(456), anyString(), eq(100L));
    }

    @Test
    void registerVote_SuccessB() {
        MemeBattle battle = new MemeBattle();
        battle.setId(100L);
        battle.setStatus("ACTIVE");
        battle.setVotesA(0);
        battle.setVotesB(0);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setTelegramChatId(123L);
        battle.setTelegramMessageId(456);
        battle.setEndTime(LocalDateTime.now().plusMinutes(10));

        when(memeBattleRepository.findById(100L)).thenReturn(Optional.of(battle));
        when(memeBattleVoteRepository.existsByBattleIdAndUserId(100L, 999L)).thenReturn(false);
        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(new MemeModeration()));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(new MemeModeration()));

        boolean result = memeBattleService.registerVote(100L, 999L, "B");

        assertTrue(result);
        assertEquals(0, battle.getVotesA());
        assertEquals(1, battle.getVotesB());
        verify(memeBattleVoteRepository).save(any(MemeBattleVote.class));
        verify(memeBattleRepository).save(battle);
        verify(telegramService).editBattleVoteCard(eq(123L), eq(456), anyString(), eq(100L));
    }

    @Test
    void registerVote_BattleNotFoundOrInactive() {
        when(memeBattleRepository.findById(100L)).thenReturn(Optional.empty());
        assertFalse(memeBattleService.registerVote(100L, 999L, "A"));

        MemeBattle inactive = new MemeBattle();
        inactive.setStatus("COMPLETED");
        when(memeBattleRepository.findById(100L)).thenReturn(Optional.of(inactive));
        assertFalse(memeBattleService.registerVote(100L, 999L, "A"));
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

        MemeModeration memeA = new MemeModeration("meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

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

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        assertEquals("meme-A", battle.getWinnerMemeId());
        verify(memeBattleRepository).save(battle);

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

        verify(telegramUserRepository).save(authorA);
        assertEquals(10, authorA.getPoints());
        assertEquals(1, authorA.getBattleWins());
        verify(telegramService).editMessageTextWithMarkdown(eq(12345L), eq(67890), anyString());
    }

    @Test
    void completeBattle_MemeBWins_DuelType() {
        MemeBattle battle = new MemeBattle();
        battle.setId(50L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setBet(100);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(5);
        battle.setVotesB(10);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        MemeModeration memeA = new MemeModeration("meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));

        MemeRating ratingA = new MemeRating("meme-A", 1000, 0, 0, null);
        MemeRating ratingB = new MemeRating("meme-B", 1000, 0, 0, null);

        when(memeRatingRepository.findById("meme-A")).thenReturn(Optional.of(ratingA));
        when(memeRatingRepository.findById("meme-B")).thenReturn(Optional.of(ratingB));

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        assertEquals("meme-B", battle.getWinnerMemeId());
        verify(memeBattleRepository).save(battle);
        verify(memeDuelLifecycleService).completeDuel(battle, "meme-B", 100);
    }

    @Test
    void completeBattle_MemesNotFound() {
        MemeBattle battle = new MemeBattle();
        battle.setId(50L);
        battle.setStatus("ACTIVE");
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.empty());
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(new MemeModeration()));

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        verify(memeBattleRepository).save(battle);
        verifyNoInteractions(memeRatingRepository);
    }

    @Test
    void completeBattle_Draw_NoWinner() {
        MemeBattle battle = new MemeBattle();
        battle.setId(50L);
        battle.setStatus("ACTIVE");
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(5);
        battle.setVotesB(5);

        MemeModeration memeA = new MemeModeration("meme-A", "file-A", "Cat meme", "", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration memeB = new MemeModeration("meme-B", "file-B", "Dog meme", "", 222L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findById("meme-A")).thenReturn(Optional.of(memeA));
        when(memeModerationRepository.findById("meme-B")).thenReturn(Optional.of(memeB));

        when(memeRatingRepository.findById("meme-A")).thenReturn(Optional.empty());
        when(memeRatingRepository.findById("meme-B")).thenReturn(Optional.empty());

        memeBattleService.completeBattle(battle);

        assertEquals("COMPLETED", battle.getStatus());
        assertNull(battle.getWinnerMemeId());
        verify(memeBattleRepository).save(battle);
    }
}
