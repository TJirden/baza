package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
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
    private TelegramService telegramService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Cache<String, byte[]> imageBytesCache;

    @InjectMocks
    private MemeAiConsumer consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "retryDelayMs", 5000L);
        ReflectionTestUtils.setField(consumer, "retryMultiplier", 2.0);
        ReflectionTestUtils.setField(consumer, "retryMaxDelayMs", 3600000L);
        ReflectionTestUtils.setField(consumer, "maxRetries", 10);
    }

    private MemeModeration pendingMeme(String id, String fileId, int retryCount) {
        MemeModeration m = new MemeModeration(
                id, fileId, "desc", "ocr", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.PENDING, "Ожидает");
        m.setRetryCount(retryCount);
        return m;
    }

    private void stubClaimSuccess(String memeId) {
        when(idRepository.claimForProcessing(memeId)).thenReturn(true);
    }

    @Test
    void processMeme_Success_PendingMeme() throws Exception {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        eq("meme-1"), any(byte[].class), anyString(), eq(111L), eq(MemeVisibility.PUBLIC), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1");

        verify(imageBytesCache).put(eq("meme-1"), any(byte[].class));
        verify(imageBytesCache).invalidate("meme-1");
        verify(telegramService).sendMessageWithMarkdown(eq(111L), contains("одобрен"));
        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_Quarantined_NotifiesUser() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.QUARANTINED_CENSORSHIP);

        consumer.processMeme("meme-1");

        verify(imageBytesCache).invalidate("meme-1");
        verify(telegramService).sendMessageWithMarkdown(eq(111L), contains("карантин"));
        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_CacheHit_SkipsDownload() throws Exception {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.APPROVED);

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
    }

    @Test
    void processMeme_AlreadyApproved_Skips() throws Exception {
        MemeModeration moderation = new MemeModeration(
                "meme-1", "file-1", "desc", "ocr", 111L, MemeVisibility.PUBLIC, "", ModerationStatus.APPROVED, null);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(idRepository, never()).claimForProcessing(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_NotFound_Skips() throws Exception {
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.empty());

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(idRepository, never()).claimForProcessing(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void processMeme_AlreadyClaimed_Skips() throws Exception {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        when(idRepository.claimForProcessing("meme-1")).thenReturn(false);

        consumer.processMeme("meme-1");

        verify(fileService, never()).downloadFileBytes(anyString());
        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    void processMeme_DownloadFails_PublishesRetry() throws Exception {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenThrow(new RuntimeException("download failed"));
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 1)));

        consumer.processMeme("meme-1");

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(idRepository).incrementRetryCount("meme-1");
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_EmptyImage_PublishesRetry() throws Exception {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(null);
        when(fileService.downloadFileBytes("file-1")).thenReturn(null);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 1)));

        consumer.processMeme("meme-1");

        verify(aiProcessingService, never())
                .processAiAndFinalize(anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString());
        verify(idRepository).incrementRetryCount("meme-1");
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_AiUnavailable_PublishesRetry() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 2);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 3)));

        consumer.processMeme("meme-1");

        verify(idRepository, never()).promoteToApproved(anyString(), any());
        verify(idRepository).incrementRetryCount("meme-1");
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
        verify(imageBytesCache, never()).invalidate(anyString());
    }

    @Test
    void processMeme_RetryCountFromDb_DeterminesBackoff() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 3);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 3)));

        consumer.processMeme("meme-1");

        verify(idRepository).incrementRetryCount("meme-1");
        ArgumentCaptor<MessagePostProcessor> mppCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), mppCaptor.capture());

        Message message = new Message("meme-1".getBytes(), new MessageProperties());
        mppCaptor.getValue().postProcessMessage(message);
        String expiration = message.getMessageProperties().getExpiration();
        long expectedDelay = 5000L * (long) Math.pow(2.0, 3 - 1);
        assertEquals(String.valueOf(expectedDelay), expiration);
    }

    @Test
    void processMeme_PermanentError_SendsToDlqAndDeletes() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenThrow(new RuntimeException("NPE somewhere"));

        consumer.processMeme("meme-1");

        verify(rabbitTemplate).convertAndSend(eq("ai.dlx"), eq("ai.process.dlq"), eq("meme-1"));
        verify(idRepository).delete("meme-1");
        verify(telegramService).sendMessageWithMarkdown(eq(111L), anyString());
        verify(imageBytesCache).invalidate("meme-1");
        verify(idRepository, never()).incrementRetryCount(anyString());
        verify(rabbitTemplate, never())
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_TransientError_PublishesRetry() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenThrow(new TransientProcessingException("DB down", new RuntimeException("conn lost")));
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 1)));

        consumer.processMeme("meme-1");

        verify(idRepository).incrementRetryCount("meme-1");
        verify(rabbitTemplate)
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), eq("meme-1"), any(MessagePostProcessor.class));
        verify(rabbitTemplate, never()).convertAndSend(eq("ai.dlx"), eq("ai.process.dlq"), eq("meme-1"));
        verify(idRepository, never()).delete(anyString());
        verify(telegramService, never()).sendMessageWithMarkdown(anyLong(), anyString());
    }

    @Test
    void processMeme_NoLongerProcessing_SkipsRetry() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(0);

        consumer.processMeme("meme-1");

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void processMeme_MemeDeletedAfterIncrement_SkipsRetry() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 0);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.empty());

        consumer.processMeme("meme-1");

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    void processMeme_MaxRetriesExceeded_SendsToDlq() {
        MemeModeration moderation = pendingMeme("meme-1", "file-1", 11);
        when(idRepository.findModerationById("meme-1")).thenReturn(Optional.of(moderation));
        stubClaimSuccess("meme-1");
        when(imageBytesCache.getIfPresent("meme-1")).thenReturn(new byte[] {1, 2, 3});
        when(aiProcessingService.processAiAndFinalize(
                        anyString(), any(byte[].class), anyString(), anyLong(), any(), anyString()))
                .thenReturn(MemeAiProcessingService.AiProcessingResult.AI_UNAVAILABLE);
        when(idRepository.incrementRetryCount("meme-1")).thenReturn(1);
        when(idRepository.findModerationById("meme-1"))
                .thenReturn(Optional.of(moderation))
                .thenReturn(Optional.of(pendingMeme("meme-1", "file-1", 11)));

        consumer.processMeme("meme-1");

        verify(rabbitTemplate).convertAndSend(eq("ai.dlx"), eq("ai.process.dlq"), eq("meme-1"));
        verify(idRepository).delete("meme-1");
        verify(telegramService).sendMessageWithMarkdown(eq(111L), anyString());
        verify(rabbitTemplate, never())
                .convertAndSend(eq("ai.dlx"), eq("ai.process.retry"), any(), any(MessagePostProcessor.class));
    }
}
