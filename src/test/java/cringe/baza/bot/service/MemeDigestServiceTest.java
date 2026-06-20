package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeDigestServiceTest {

    @Mock
    private MemeGroupRepository groupRepository;

    @Mock
    private MemeModerationRepository moderationRepository;

    @Mock
    private MemeRatingRepository ratingRepository;

    @Mock
    private TelegramUserRepository userRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private TelegramBot bot;

    @InjectMocks
    private MemeDigestService digestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(digestService, "digestDays", 7);
    }

    @Test
    void getTopMemesForGroup_Success() {
        // Arrange
        Long groupId = 10L;
        MemeModeration meme1 =
                new MemeModeration("meme-1", "file-1", "Desc 1", "OCR 1", 111L, "GROUP", "10", "APPROVED", null);
        MemeModeration meme2 =
                new MemeModeration("meme-2", "file-2", "Desc 2", "OCR 2", 222L, "GROUP", "10,20", "APPROVED", null);
        MemeModeration meme3 =
                new MemeModeration("meme-3", "file-3", "Desc 3", "OCR 3", 111L, "GROUP", "10", "APPROVED", null);
        MemeModeration memeOther = new MemeModeration(
                "meme-other", "file-other", "Desc O", "OCR O", 111L, "GROUP", "20", "APPROVED", null);

        when(moderationRepository.findByStatusAndCreatedAtAfter(eq("APPROVED"), any()))
                .thenReturn(List.of(meme1, meme2, meme3, memeOther));

        MemeRating r1 = new MemeRating("meme-1", 1100, 2, 0, null);
        MemeRating r2 = new MemeRating("meme-2", 1200, 4, 1, null);
        MemeRating r3 = new MemeRating("meme-3", 1000, 0, 0, null);

        when(ratingRepository.findAllById(List.of("meme-1", "meme-2", "meme-3")))
                .thenReturn(List.of(r1, r2, r3));

        // Act
        List<MemeModeration> result = digestService.getTopMemesForGroup(groupId);

        // Assert
        assertEquals(3, result.size());
        assertEquals("meme-2", result.get(0).getId()); // Highest Elo: 1200
        assertEquals("meme-1", result.get(1).getId()); // 1100
        assertEquals("meme-3", result.get(2).getId()); // 1000
    }

    @Test
    void generateDigestTextWithAI_Success() {
        // Arrange
        String groupName = "Cringe Group";
        MemeModeration meme =
                new MemeModeration("meme-1", "file-1", "A dog", "Hello", 111L, "GROUP", "10", "APPROVED", null);

        TelegramUser user = new TelegramUser();
        user.setId(111L);
        user.setUsername("dog_owner");

        when(userRepository.findById(111L)).thenReturn(Optional.of(user));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);
        when(mockMessage.getText()).thenReturn("Generated digest text from Gemini!");
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // Act
        String result = digestService.generateDigestTextWithAI(groupName, List.of(meme));

        // Assert
        assertEquals("Generated digest text from Gemini!", result);
    }

    @Test
    void generateAndSendDigestForGroup_Success() {
        // Arrange
        MemeGroup group = new MemeGroup();
        group.setId(10L);
        group.setName("Meme Club");

        TelegramUser member = new TelegramUser();
        member.setId(999L);
        member.setUsername("member1");
        group.setMembers(Set.of(member));

        MemeModeration meme =
                new MemeModeration("meme-1", "file-1", "Desc", "OCR", 111L, "GROUP", "10", "APPROVED", null);

        // Set up recent memes and ratings to return top memes
        when(moderationRepository.findByStatusAndCreatedAtAfter(eq("APPROVED"), any()))
                .thenReturn(List.of(meme));
        when(ratingRepository.findAllById(anyList())).thenReturn(List.of(new MemeRating("meme-1", 1000, 0, 0, null)));

        // Mock AI generation
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);
        when(mockMessage.getText()).thenReturn("Cool Digest");
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // Mock user details for owner
        TelegramUser owner = new TelegramUser();
        owner.setId(111L);
        owner.setUsername("owner1");
        when(userRepository.findById(111L)).thenReturn(Optional.of(owner));

        // Act
        digestService.generateAndSendDigestForGroup(group);

        // Assert
        verify(bot).execute(any(SendMessage.class));
        verify(bot).execute(any(SendPhoto.class));
    }
}
