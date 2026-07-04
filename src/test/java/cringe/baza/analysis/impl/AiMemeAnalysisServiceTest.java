package cringe.baza.analysis.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.CensorshipResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class AiMemeAnalysisServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private TelegramFileService fileService;

    private AiMemeAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AiMemeAnalysisService(chatModel, fileService);
    }

    @Test
    void testCacheHitsAndSingleAiCall() throws IOException {
        String fileId = "test-file-123";
        byte[] dummyBytes = new byte[] {1, 2, 3};

        when(fileService.downloadFile(fileId)).thenReturn(new ByteArrayInputStream(dummyBytes));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage assistantMessage =
                new AssistantMessage("SAFE: TRUE\nREASON: \nTEXT: Hello World\nDESCRIPTION: A funny meme description");

        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(assistantMessage);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // 1. First call (Censorship) -> should invoke ChatModel
        CensorshipResult censorship = analysisService.checkCensorship(fileId);
        assertTrue(censorship.safe());
        assertEquals("", censorship.reason());

        // 2. Second call (Description) -> should hit Cache, no ChatModel call
        String description = analysisService.generateDescription(fileId);
        assertEquals("A funny meme description", description);

        // 3. Third call (OCR) -> should hit Cache, no ChatModel call
        String ocrText = analysisService.extractText(fileId);
        assertEquals("Hello World", ocrText);

        // Verify chatModel.call was called exactly ONCE
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void testCacheEvictionUnderMemoryPressure() throws IOException {
        String fileId = "test-file-123";
        byte[] dummyBytes = new byte[] {1, 2, 3};

        // We return the stream twice because we expect two downloads: before and after eviction
        when(fileService.downloadFile(fileId))
                .thenReturn(new ByteArrayInputStream(dummyBytes))
                .thenReturn(new ByteArrayInputStream(dummyBytes));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage assistantMessage =
                new AssistantMessage("SAFE: TRUE\nREASON: \nTEXT: Hello World\nDESCRIPTION: A funny meme description");

        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(assistantMessage);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // First call triggers AI analysis and caches it
        String description1 = analysisService.generateDescription(fileId);
        assertEquals("A funny meme description", description1);
        verify(chatModel, times(1)).call(any(Prompt.class));

        // Trigger memory pressure to force SoftReference eviction in Caffeine
        try {
            List<byte[]> allocation = new ArrayList<>();
            while (true) {
                // Keep allocating 10MB arrays to deplete heap
                allocation.add(new byte[10 * 1024 * 1024]);
            }
        } catch (OutOfMemoryError e) {
            // Heap is depleted, JVM should evict soft references
        }

        // Call again. Since the cache has softValues, it should have been evicted under OOM
        String description2 = analysisService.generateDescription(fileId);
        assertEquals("A funny meme description", description2);

        // Should have called the AI model again (total of 2 times)
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
