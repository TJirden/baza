package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import cringe.baza.model.MemeVisibility;
import cringe.baza.repository.MemeProcessingRepository;
import cringe.baza.repository.MemeReadRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeAiProcessingServiceTest {

    @Mock
    private MemeAnalyzerService memeAnalyzerService;

    @Mock
    private MemeReadRepository readRepository;

    @Mock
    private MemeProcessingRepository processingRepository;

    private MemeAiProcessingService service;

    @BeforeEach
    void setUp() {
        service = new MemeAiProcessingService(memeAnalyzerService, readRepository, processingRepository);
        ReflectionTestUtils.setField(service, "phashThreshold", 5);
    }

    @Test
    void processAiAndFinalize_DuplicateDetected_Quarantines() {
        when(memeAnalyzerService.analyze(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("ocr", "desc", true, ""));
        when(readRepository.findApprovedDuplicate("meme-1", 5)).thenReturn(Optional.of("meme-original"));
        when(processingRepository.updateToQuarantinedIfPending(eq("meme-1"), anyString(), anyString(), anyString()))
                .thenReturn(true);

        MemeAiProcessingService.AiProcessingResult result = service.processAiAndFinalize(
                "meme-1", new byte[] {1, 2, 3}, "user desc", 111L, MemeVisibility.PUBLIC, "");

        assertEquals(MemeAiProcessingService.AiProcessingResult.QUARANTINED_DUPLICATE, result);
        verify(processingRepository)
                .updateToQuarantinedIfPending(eq("meme-1"), anyString(), anyString(), contains("Визуальный дубликат"));
        verify(processingRepository, never()).promoteToApproved(anyString(), any());
    }

    @Test
    void processAiAndFinalize_NoDuplicate_PromotesToApproved() {
        when(memeAnalyzerService.analyze(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("ocr", "desc", true, ""));
        when(readRepository.findApprovedDuplicate("meme-1", 5)).thenReturn(Optional.empty());
        when(processingRepository.promoteToApproved(eq("meme-1"), any())).thenReturn(true);

        MemeAiProcessingService.AiProcessingResult result = service.processAiAndFinalize(
                "meme-1", new byte[] {1, 2, 3}, "user desc", 111L, MemeVisibility.PUBLIC, "");

        assertEquals(MemeAiProcessingService.AiProcessingResult.APPROVED, result);
        verify(processingRepository).promoteToApproved(eq("meme-1"), any());
    }

    @Test
    void processAiAndFinalize_NotSafe_QuarantinesCensorship() {
        when(memeAnalyzerService.analyze(any(byte[].class)))
                .thenReturn(new MemeAnalyzerService.MemeAnalysis("ocr", "desc", false, "NSFW"));
        when(processingRepository.updateToQuarantinedIfPending(eq("meme-1"), anyString(), anyString(), anyString()))
                .thenReturn(true);

        MemeAiProcessingService.AiProcessingResult result = service.processAiAndFinalize(
                "meme-1", new byte[] {1, 2, 3}, "user desc", 111L, MemeVisibility.PUBLIC, "");

        assertEquals(MemeAiProcessingService.AiProcessingResult.QUARANTINED_CENSORSHIP, result);
        verify(readRepository, never()).findApprovedDuplicate(anyString(), anyInt());
        verify(processingRepository, never()).promoteToApproved(anyString(), any());
    }
}
