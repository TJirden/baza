package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.PhotoSize;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.meme.phash.MemeImageHasher;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.MemeVectorRepository;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncMemeServiceTest {

    private static final long SAMPLE_HASH = 0xDEADBEEFL;

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
    private MemeImageHasher memeImageHasher;

    @Mock
    private MemeImageHashRepository memeImageHashRepository;

    @InjectMocks
    private AsyncMemeService asyncMemeService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().doReturn(new byte[] {1, 2, 3}).when(fileService).downloadFileBytes(anyString());
        lenient().when(memeImageHasher.computeHash(any(byte[].class))).thenReturn(OptionalLong.of(SAMPLE_HASH));
        lenient().when(memeImageHashRepository.findNearest(anyLong(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void saveMemeAsync_Success_Public() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("puppy text ocr", "puppy playing tag"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class), any(OptionalLong.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals(MemeVisibility.PUBLIC, savedMeme.visibility());
        assertEquals(userId, savedMeme.ownerId());
        assertEquals("Cute puppy\n\n[ИИ-Теги]: puppy playing tag", savedMeme.description());
        assertEquals("puppy text ocr", savedMeme.ocrText());
        assertEquals(0, savedMeme.groupIds().size());

        verify(memeImageHasher, times(1)).computeHash(any(byte[].class));

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
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("cat text ocr", "AI generated description of a cat"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class), any(OptionalLong.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals(MemeVisibility.PUBLIC, savedMeme.visibility());
        assertEquals("AI generated description of a cat", savedMeme.description());
        assertEquals("cat text ocr", savedMeme.ocrText());

        verify(memeImageHasher, times(1)).computeHash(any(byte[].class));

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
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class))).thenThrow(new RuntimeException("AI offline"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class), any(OptionalLong.class))).thenReturn("meme-uuid-1");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-123", savedMeme.fileId());
        assertEquals("Cute puppy", savedMeme.description());
        assertEquals("", savedMeme.ocrText());

        verify(memeImageHasher, times(1)).computeHash(any(byte[].class));

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
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("cat text ocr", "cat jumping around"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble())).thenReturn(Optional.empty());
        when(memeProcessor.save(any(Meme.class), any(OptionalLong.class))).thenReturn("meme-uuid-2");

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
        verify(memeProcessor).save(memeCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        Meme savedMeme = memeCaptor.getValue();
        assertEquals("file-456", savedMeme.fileId());
        assertEquals(MemeVisibility.GROUP, savedMeme.visibility());
        assertEquals(List.of(10L, 20L), savedMeme.groupIds());
        assertEquals("Funny cat\n\n[ИИ-Теги]: cat jumping around", savedMeme.description());
        assertEquals("cat text ocr", savedMeme.ocrText());

        verify(memeImageHasher, times(1)).computeHash(any(byte[].class));

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
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("unsafe text ocr", "unsafe image contents"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(false, "Подозрение на NSFW"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(memeImageHasher, times(1)).computeHash(any(byte[].class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeVectorRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
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
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(
                        new MemeAnalyzerService.MemeAnalysis("duplicate text ocr", "duplicate content description"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble()))
                .thenReturn(Optional.of("existing-meme-id-999"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeVectorRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Дубликат мема: existing-meme-id-999", moderation.getModerationReason());
        assertEquals("duplicate text ocr", moderation.getOcrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_VisualDuplicateFlagged() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Visual duplicate";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-vis-dup");
        when(memeImageHashRepository.findNearest(anyLong(), anyInt())).thenReturn(Optional.of("existing-vis-id"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(memeAnalyzerService, never()).analyzeMemeDetails(any(byte[].class));
        verify(memeAnalyzerService, never()).checkCensorship(any(byte[].class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeVectorRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Визуальный дубликат мема: existing-vis-id", moderation.getModerationReason());
        assertEquals("", moderation.getOcrText());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_VisualDedupDbError() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeImageHashRepository.findNearest(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("db connection lost"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(memeAnalyzerService, never()).analyzeMemeDetails(any(byte[].class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeVectorRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Ошибка визуальной проверки дубликатов: db connection lost", moderation.getModerationReason());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_TextDedupDbError() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeAnalyzerService.analyzeMemeDetails(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("puppy text ocr", "puppy playing tag"));
        when(memeAnalyzerService.checkCensorship(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.CensorshipResult(true, ""));
        when(memeVectorRepository.findDuplicateMemeId(anyString(), anyDouble()))
                .thenThrow(new RuntimeException("vector store down"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeVectorRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Ошибка текстовой проверки дубликатов: vector store down", moderation.getModerationReason());

        verify(telegramService, atLeastOnce()).editMessageText(eq(chatId), eq(messageIdToEdit), anyString());
        verify(telegramService, atLeastOnce())
                .editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_HashFails() {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(memeImageHasher.computeHash(any(byte[].class))).thenReturn(OptionalLong.empty());

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(memeVectorRepository, never()).saveQuarantined(any(MemeModeration.class), any(OptionalLong.class));
        verify(memeAnalyzerService, never()).analyzeMemeDetails(any(byte[].class));

        verify(telegramService).editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }

    @Test
    void saveMemeAsync_Failure_DownloadFails() throws Exception {
        long chatId = 111L;
        long userId = 222L;
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        String description = "Cute puppy";
        String visibilityContext = "PUBLIC";
        int messageIdToEdit = 500;

        when(fileService.getImageFileId(photo)).thenReturn("file-123");
        when(fileService.downloadFileBytes(anyString())).thenThrow(new RuntimeException("download failed"));

        asyncMemeService.saveMemeAsync(chatId, userId, photo, description, visibilityContext, messageIdToEdit);

        verify(memeImageHasher, never()).computeHash(any(byte[].class));
        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(memeVectorRepository, never()).saveQuarantined(any(MemeModeration.class), any(OptionalLong.class));

        verify(telegramService).editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
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

        verify(memeProcessor, never()).save(any(Meme.class), any(OptionalLong.class));
        verify(telegramService).editMessageTextWithMarkdown(eq(chatId), eq(messageIdToEdit), anyString());
    }
}
