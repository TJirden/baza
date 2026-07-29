package cringe.baza.meme;

import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeAiConsumerTest {

    @Mock
    private MemeAiProcessingService aiProcessingService;

    @Mock
    private IdRepository idRepository;

    @Mock
    private TelegramFileService fileService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Cache<String, byte[]> imageBytesCache;

    @InjectMocks
    private MemeAiConsumer consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "maxRetries", 5);
        ReflectionTestUtils.setField(consumer, "retryDelayMs", 5000L);
        ReflectionTestUtils.setField(consumer, "retryMultiplier", 2.0);
    }

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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        eq("meme-1"), any(byte[].class), anyString(), eq(111L), eq(MemeVisibility.PUBLIC), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1", null);

        verify(imageBytesCache).put(eq("meme-1"), any(byte[].class));
        verify(imageBytesCache).invalidate("meme-1");
        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_CacheHit_SkipsDownload() throws Exception {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1", null);

        verify(fileService, never()).downloadFileBytes(anyString());
    }

    @Test
    void processMeme_AlreadyApproved_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1", "file-1", "desc", "ocr", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));

        consumer.processMeme("meme-1", null);

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_NotFound_Skips() throws Exception {
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.empty());

        consumer.processMeme("meme-1", null);

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_DownloadFails_PublishesRetry() throws Exception {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenThrow(new RuntimeException("download failed"));

        consumer.processMeme("meme-1", null);

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_EmptyImage_SkipsWithoutRetry() throws Exception {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenReturn(null);

        consumer.processMeme("meme-1", null);

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_AiUnavailable_PublishesRetry() {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);

        consumer.processMeme("meme-1", null);

        verify(idRepository, never()).promoteToApproved(anyString(), any());
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
        verify(imageBytesCache, never()).invalidate(anyString());
    }

    @Test
    void processMeme_MaxRetriesExceeded_ThrowsForDlq() {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);

        org.junit.jupiter.api.Assertions.assertThrows(
                AmqpRejectAndDontRequeueException.class, () -> consumer.processMeme("meme-1", 4));

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_UnexpectedException_PublishesRetry() {
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
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenThrow(new RuntimeException("NPE somewhere"));

        consumer.processMeme("meme-1", null);

        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
    }
}
