package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.pengrad.telegrambot.model.PhotoSize;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.meme.phash.MemeImageHasher;
import cringe.baza.model.IdRepository;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeImageHashRepository;
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
    private TelegramFileService fileService;

    @Mock
    private MemeImageHasher memeImageHasher;

    @Mock
    private MemeImageHashRepository memeImageHashRepository;

    @Mock
    private IdRepository memeRepository;

    @Mock
    private MemeAiProcessingService aiProcessingService;

    @Mock
    private MemeAiProducer aiProducer;

    @Mock
    private Cache<String, byte[]> imageBytesCache;

    @InjectMocks
    private AsyncMemeService asyncMemeService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().doReturn(new byte[] {1, 2, 3}).when(fileService).downloadFileBytes(anyString());
        lenient().when(memeImageHasher.computeHash(any(byte[].class))).thenReturn(OptionalLong.of(SAMPLE_HASH));
        lenient()
                .when(memeImageHashRepository.findNearestApproved(anyLong(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void saveMemeAsync_Success_Public() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        ArgumentCaptor<MemeModeration> pendingCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeRepository).savePending(pendingCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration pending = pendingCaptor.getValue();
        assertEquals(ModerationStatus.PENDING, pending.getStatus());
        assertEquals("Cute puppy", pending.getDescription());
        assertEquals(MemeVisibility.PUBLIC, pending.getVisibility());

        verify(aiProcessingService)
                .processAiAndFinalize(
                        anyString(),
                        any(byte[].class),
                        eq("Cute puppy"),
                        eq(222L),
                        eq(MemeVisibility.PUBLIC),
                        anyString());
        verify(telegramService, atLeastOnce()).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void saveMemeAsync_Success_Group() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-456");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Funny cat", "GROUP:10,20", 500);

        ArgumentCaptor<MemeModeration> pendingCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeRepository).savePending(pendingCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration pending = pendingCaptor.getValue();
        assertEquals(MemeVisibility.GROUP, pending.getVisibility());
        assertEquals("10,20", pending.getGroupIds());

        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void saveMemeAsync_CensorshipFlagged() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-unsafe");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.QUARANTINED_CENSORSHIP);

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Unsafe meme", "PUBLIC", 500);

        verify(memeRepository).savePending(any(MemeModeration.class), eq(OptionalLong.of(SAMPLE_HASH)));
        verify(telegramService, atLeastOnce()).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void saveMemeAsync_DuplicateFlagged() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-dup");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.QUARANTINED_DUPLICATE);

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Duplicate meme", "PUBLIC", 500);

        verify(memeRepository).savePending(any(MemeModeration.class), eq(OptionalLong.of(SAMPLE_HASH)));
        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void saveMemeAsync_AiUnavailable_Enqueues() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        verify(memeRepository).savePending(any(MemeModeration.class), eq(OptionalLong.of(SAMPLE_HASH)));
        verify(aiProducer).enqueueForProcessing(anyString());
        verify(telegramService, atLeastOnce()).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
    }

    @Test
    void saveMemeAsync_TransientError_EnqueuesAndSoftMessage() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenThrow(new TransientProcessingException("DB down", new RuntimeException("conn lost")));

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        verify(memeRepository).savePending(any(MemeModeration.class), eq(OptionalLong.of(SAMPLE_HASH)));
        verify(aiProducer).enqueueForProcessing(anyString());
        verify(telegramService).editMessageTextWithMarkdown(eq(111L), eq(500), contains("поставлен в очередь"));
    }

    @Test
    void saveMemeAsync_VisualDuplicateFlagged() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-vis-dup");
        when(memeImageHashRepository.findNearestApproved(anyLong(), anyInt()))
                .thenReturn(Optional.of("existing-vis-id"));

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Visual duplicate", "PUBLIC", 500);

        verify(memeRepository, never()).savePending(any(MemeModeration.class), any(OptionalLong.class));
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        MemeModeration moderation = moderationCaptor.getValue();
        assertEquals(ModerationStatus.QUARANTINED, moderation.getStatus());
        assertEquals("Визуальный дубликат мема: existing-vis-id", moderation.getModerationReason());
    }

    @Test
    void saveMemeAsync_VisualDedupDbError() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        when(memeImageHashRepository.findNearestApproved(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("db connection lost"));

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        verify(memeRepository, never()).savePending(any(MemeModeration.class), any(OptionalLong.class));
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());

        ArgumentCaptor<MemeModeration> moderationCaptor = ArgumentCaptor.forClass(MemeModeration.class);
        verify(memeRepository).saveQuarantined(moderationCaptor.capture(), eq(OptionalLong.of(SAMPLE_HASH)));
        assertEquals(ModerationStatus.QUARANTINED, moderationCaptor.getValue().getStatus());
        assertEquals(
                "Ошибка визуальной проверки дубликатов: db connection lost",
                moderationCaptor.getValue().getModerationReason());
    }

    @Test
    void saveMemeAsync_HashFails() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        when(memeImageHasher.computeHash(any(byte[].class))).thenReturn(OptionalLong.empty());

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        verify(memeRepository, never()).savePending(any(MemeModeration.class), any(OptionalLong.class));
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(telegramService).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
    }

    @Test
    void saveMemeAsync_DownloadFails() throws Exception {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn("file-123");
        lenient()
                .doThrow(new RuntimeException("download failed"))
                .when(fileService)
                .downloadFileBytes(anyString());

        asyncMemeService.saveMemeAsync(
                111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Cute puppy", "PUBLIC", 500);

        verify(memeImageHasher, never()).computeHash(any(byte[].class));
        verify(memeRepository, never()).savePending(any(MemeModeration.class), any(OptionalLong.class));
        verify(telegramService).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
    }

    @Test
    void saveMemeAsync_NullFileId() {
        when(fileService.getImageFileId(any(PhotoSize[].class))).thenReturn(null);

        asyncMemeService.saveMemeAsync(111L, 222L, new PhotoSize[] {mock(PhotoSize.class)}, "Funny cat", "PUBLIC", 500);

        verify(memeRepository, never()).savePending(any(MemeModeration.class), any(OptionalLong.class));
        verify(telegramService).editMessageTextWithMarkdown(eq(111L), eq(500), anyString());
    }
}
