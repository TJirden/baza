package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

@ExtendWith(MockitoExtension.class)
class MemeAnalyzerServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private RetryTemplate retryTemplate;

    private MemeAnalyzerService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MemeAnalyzerService(chatModel, retryTemplate);
        lenient().when(retryTemplate.execute(any(Retryable.class))).thenAnswer(invocation -> {
            Retryable<?> retryable = invocation.getArgument(0);
            return retryable.execute();
        });
    }

    private void mockReply(String reply) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        var generation = mock(org.springframework.ai.chat.model.Generation.class);
        var output = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(output.getText()).thenReturn(reply);
        when(generation.getOutput()).thenReturn(output);
        when(mockResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }

    @Test
    void analyze_SafeMeme_WithText() {
        mockReply("TEXT: Hello World\nSAFE: TRUE\nREASON:\nDESCRIPTION: Котик играет с мячом");

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("Hello World", result.ocrText());
        assertEquals("Котик играет с мячом", result.description());
        assertTrue(result.safe());
        assertEquals("", result.censorshipReason());
    }

    @Test
    void analyze_UnsafeMeme() {
        mockReply("TEXT: Some text\nSAFE: FALSE\nREASON: NSFW content detected\nDESCRIPTION: Explicit image");

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("Some text", result.ocrText());
        assertFalse(result.safe());
        assertEquals("NSFW content detected", result.censorshipReason());
        assertEquals("Explicit image", result.description());
    }

    @Test
    void analyze_NoText_EMPTY() {
        mockReply("TEXT: EMPTY\nSAFE: TRUE\nREASON:\nDESCRIPTION: Забавный мем");

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("", result.ocrText());
        assertTrue(result.safe());
        assertEquals("Забавный мем", result.description());
    }

    @Test
    void analyze_MultilineDescription() {
        mockReply("TEXT: Hello\nSAFE: TRUE\nREASON:\nDESCRIPTION: Первая строка\nВторая строка\nТретья строка");

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("Hello", result.ocrText());
        assertEquals("Первая строка\nВторая строка\nТретья строка", result.description());
        assertTrue(result.safe());
    }

    @Test
    void analyze_BlankDescription_DefaultsToBezOpisaniya() {
        mockReply("TEXT: Hello\nSAFE: TRUE\nREASON:\nDESCRIPTION:");

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("Без описания", result.description());
    }

    @Test
    void analyze_AiFailure_ThrowsAiUnavailableException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI offline"));

        assertThrows(AiUnavailableException.class, () -> service.analyze(new byte[] {1, 2, 3}));
    }

    @Test
    void analyze_NullReply_ReturnsDefaults() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        var generation = mock(org.springframework.ai.chat.model.Generation.class);
        var output = mock(AssistantMessage.class);
        when(output.getText()).thenReturn(null);
        when(generation.getOutput()).thenReturn(output);
        when(mockResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        MemeAnalyzerService.MemeAnalysis result = service.analyze(new byte[] {1, 2, 3});

        assertEquals("", result.ocrText());
        assertEquals("Без описания", result.description());
        assertTrue(result.safe());
    }
}
