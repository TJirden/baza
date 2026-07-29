package cringe.baza.meme;

import static org.mockito.Mockito.*;

import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeAiConsumerTest {

    @Mock
    private MemeAiProcessingService aiProcessingService;

    @Mock
    private IdRepository idRepository;

    @Mock
    private TelegramFileService fileService;

    @InjectMocks
    private MemeAiConsumer consumer;

    @Test
    void processMeme_Success_PendingMeme() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "desc",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает AI-обработки");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(fileService.downloadFileBytes("file-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        eq("meme-1"), any(byte[].class), anyString(), eq(111L), eq(MemeVisibility.PUBLIC), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1");

        verify(aiProcessingService)
                .processAiAndFinalize(
                        eq("meme-1"), any(byte[].class), anyString(), eq(111L), eq(MemeVisibility.PUBLIC), anyString());
    }

    @Test
    void processMeme_AlreadyApproved_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1", "file-1", "desc", "ocr", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_AlreadyQuarantined_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "desc",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.QUARANTINED,
                "reason");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_NotFound_Skips() throws Exception {
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.empty());

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_DownloadFails_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "desc",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(fileService.downloadFileBytes("file-1")).thenThrow(new RuntimeException("download failed"));

        consumer.processMeme("meme-1");

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_EmptyImage_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "desc",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(fileService.downloadFileBytes("file-1")).thenReturn(null);

        consumer.processMeme("meme-1");

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_AiUnavailable_LeavesPending() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "desc",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(fileService.downloadFileBytes("file-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);

        consumer.processMeme("meme-1");

        verify(idRepository, never()).promoteToApproved(anyString(), any());
    }

    @Test
    void processMeme_ExtractsUserDescription_FromAiTags() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1",
                "file-1",
                "My cool meme\n\n[ИИ-Теги]: cat",
                "ocr",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает");
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(fileService.downloadFileBytes("file-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        eq("meme-1"),
                        any(byte[].class),
                        eq("My cool meme"),
                        eq(111L),
                        eq(MemeVisibility.PUBLIC),
                        anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1");

        verify(aiProcessingService)
                .processAiAndFinalize(
                        eq("meme-1"),
                        any(byte[].class),
                        eq("My cool meme"),
                        eq(111L),
                        eq(MemeVisibility.PUBLIC),
                        anyString());
    }
}
