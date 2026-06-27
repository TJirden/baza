package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.PhotoSize;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.MemeVectorRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncMemeServiceTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private MemeProcessor memeProcessor;

    @Mock
    private TelegramFileService fileService;

    @Mock
    private MemeAnalyzerService memeAnalyzerService;

    @Mock
    private MemeVectorRepository memeVectorRepository;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @InjectMocks
    private AsyncMemeService asyncMemeService;

    @Test
    void saveMemeAsync_Success_Public() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeAnalyzerService.analyzeMemeDetails("file-123"))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("puppy text ocr", "puppy playing tag"));
        when(memeAnalyzerService.checkCensorship("file-123"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture());
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals(MemeVisibility.PUBLIC, savedMeme.visibility());
        assertEquals(userId, savedMeme.ownerId());
        assertEquals("Cute puppy\n\n[ИИ-Теги]: puppy playing tag", savedMeme.description());
        assertEquals("puppy text ocr", savedMeme.ocrText());
        assertEquals(0, savedMeme.groupIds().size());

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeModerationRepository).save(moderationCaptor.capture());
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.APPROVED, moderation.getStatus());
        assertEquals("file-123", moderation.getFileId());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Success_NoDescription() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = null;
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeAnalyzerService.analyzeMemeDetails("file-123"))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("cat text ocr", "AI generated description of a cat"));
        when(memeAnalyzerService.checkCensorship("file-123"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture());
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals(MemeVisibility.PUBLIC, savedMeme.visibility());
        assertEquals("AI generated description of a cat", savedMeme.description());
        assertEquals("cat text ocr", savedMeme.ocrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Success_WithDescription_AIEnrichmentFailure() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeAnalyzerService.analyzeMemeDetails("file-123")).thenThrow(new RuntimeException("AI offline"));
        when(memeAnalyzerService.checkCensorship("file-123"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture());
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals("Cute puppy", savedMeme.description());
        assertEquals("", savedMeme.ocrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Success_Group() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Funny cat";
        String visibilityContext = "GROUP:10,20";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-456");
        when(memeAnalyzerService.analyzeMemeDetails("file-456"))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("cat text ocr", "cat jumping around"));
        when(memeAnalyzerService.checkCensorship("file-456"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-2");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture());
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-456", savedMeme.fileId());
        assertEquals(MemeVisibility.GROUP, savedMeme.visibility());
        assertEquals(List.of(10L, 20L), savedMeme.groupIds());
        assertEquals("Funny cat\n\n[ИИ-Теги]: cat jumping around", savedMeme.description());
        assertEquals("cat text ocr", savedMeme.ocrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_CensorshipFlagged() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Unsafe meme";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-unsafe");
        when(memeAnalyzerService.analyzeMemeDetails("file-unsafe"))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("unsafe text ocr", "unsafe image contents"));
        when(memeAnalyzerService.checkCensorship("file-unsafe"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(false, "Подозрение на NSFW"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeModerationRepository).save(moderationCaptor.capture());
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("ИИ-цензура: Подозрение на NSFW", moderation.getModerationReason());
        assertEquals("unsafe text ocr", moderation.getOcrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_DuplicateFlagged() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Duplicate meme";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-duplicate");
        when(memeAnalyzerService.analyzeMemeDetails("file-duplicate"))
                .thenReturn(
                        new MemeAnalyzerService.MemeAnalysis("duplicate text ocr", "duplicate content description"));
        when(memeAnalyzerService.checkCensorship("file-duplicate"))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble()))
                .thenReturn(Optional.of("existing-meme-id-999"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeModerationRepository).save(moderationCaptor.capture());
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Дубликат мема: existing-meme-id-999", moderation.getModerationReason());
        assertEquals("duplicate text ocr", moderation.getOcrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_NullFileId() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Funny cat";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn(null);

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class));
        verify(telegramService).editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }
}
