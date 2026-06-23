package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.bot.model.UserState;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.MemeSwipeVoteRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SwipeServiceTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeRatingRepository memeRatingRepository;

    @Mock
    private MemeSwipeVoteRepository swipeVoteRepository;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @Mock
    private UserSessionService sessionService;

    @InjectMocks
    private SwipeService swipeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(swipeService, "defaultElo", 1000);
    }

    @Test
    void getNextMemeForUser_NoCandidates() {
        long userId = 123L;
        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(Collections.emptyList());
        when(swipeVoteRepository.findVotedMemeIdsByUserId(userId)).thenReturn(Collections.emptyList());

        Optional<MemeModeration> result = swipeService.getNextMemeForUser(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getNextMemeForUser_HasCandidates() {
        long userId = 123L;

        MemeModeration ownMeme = new MemeModeration(
                "meme-1", "file-1", "Own meme", "", userId, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration alreadyVoted = new MemeModeration(
                "meme-2", "file-2", "Voted meme", "", 456L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeModeration candidate = new MemeModeration(
                "meme-3",
                "file-3",
                "Good candidate",
                "",
                456L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.APPROVED,
                null);

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(ownMeme, alreadyVoted, candidate));
        when(swipeVoteRepository.findVotedMemeIdsByUserId(userId)).thenReturn(List.of("meme-2"));

        Optional<MemeModeration> result = swipeService.getNextMemeForUser(userId);

        assertTrue(result.isPresent());
        assertEquals("meme-3", result.get().getId());
    }

    @Test
    void registerSwipeVote_AlreadyVoted() {
        long userId = 123L;
        String memeId = "meme-1";
        when(swipeVoteRepository.existsByMemeIdAndUserId(memeId, userId)).thenReturn(true);

        swipeService.registerSwipeVote(userId, memeId, "BASE");

        verify(swipeVoteRepository, never()).save(any());
        verify(memeRatingRepository, never()).save(any());
    }

    @Test
    void registerSwipeVote_BaseSuccess() {
        long userId = 123L;
        String memeId = "meme-1";
        long ownerId = 456L;

        MemeModeration meme = new MemeModeration(
                memeId,
                "file-1",
                "Description",
                "",
                ownerId,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.APPROVED,
                null);
        MemeRating rating = new MemeRating(memeId, 1000, 0, 0, null);
        TelegramUser owner = new TelegramUser(ownerId, "owner", "OwnerName", 0, 100, new java.util.HashSet<>());

        when(swipeVoteRepository.existsByMemeIdAndUserId(memeId, userId)).thenReturn(false);
        when(memeModerationRepository.findById(memeId)).thenReturn(Optional.of(meme));
        when(memeRatingRepository.findById(memeId)).thenReturn(Optional.of(rating));
        when(telegramUserRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        swipeService.registerSwipeVote(userId, memeId, "BASE");

        verify(swipeVoteRepository).save(any());
        assertEquals(1008, rating.getEloRating());
        assertEquals(1, rating.getWins());
        assertEquals(101, owner.getPoints());
        verify(memeRatingRepository).save(rating);
        verify(telegramUserRepository).save(owner);
    }

    @Test
    void registerSwipeVote_CringeSuccess() {
        long userId = 123L;
        String memeId = "meme-1";

        MemeModeration meme = new MemeModeration(
                memeId, "file-1", "Description", "", null, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        MemeRating rating = new MemeRating(memeId, 1000, 0, 0, null);

        when(swipeVoteRepository.existsByMemeIdAndUserId(memeId, userId)).thenReturn(false);
        when(memeModerationRepository.findById(memeId)).thenReturn(Optional.of(meme));
        when(memeRatingRepository.findById(memeId)).thenReturn(Optional.of(rating));

        swipeService.registerSwipeVote(userId, memeId, "CRINGE");

        verify(swipeVoteRepository).save(any());
        assertEquals(992, rating.getEloRating());
        assertEquals(1, rating.getLosses());
        verify(memeRatingRepository).save(rating);
        verifyNoInteractions(telegramUserRepository);
    }

    @Test
    void sendSwipeCard_NoMeme() {
        long chatId = 10L;
        long userId = 123L;

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(Collections.emptyList());
        when(swipeVoteRepository.findVotedMemeIdsByUserId(userId)).thenReturn(Collections.emptyList());

        swipeService.sendSwipeCard(chatId, userId);

        verify(telegramService).sendMessageWithMarkdown(eq(chatId), anyString());
        verify(sessionService).setUserState(chatId, UserState.DEFAULT);
    }

    @Test
    void sendSwipeCard_MemeExists() {
        long chatId = 10L;
        long userId = 123L;
        MemeModeration meme = new MemeModeration(
                "meme-1", "file-1", "Cool meme", "", 456L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);

        when(memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(meme));
        when(swipeVoteRepository.findVotedMemeIdsByUserId(userId)).thenReturn(Collections.emptyList());
        when(memeRatingRepository.findById("meme-1")).thenReturn(Optional.empty());

        swipeService.sendSwipeCard(chatId, userId);

        verify(telegramService).sendSwipeCard(eq(chatId), eq("file-1"), anyString(), eq("meme-1"));
        verifyNoInteractions(sessionService);
    }
}
