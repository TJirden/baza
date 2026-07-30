package cringe.baza.meme;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeReprocessSchedulerTest {

    @Mock
    private MemeModerationRepository moderationRepository;

    @Mock
    private MemeAiProducer aiProducer;

    @InjectMocks
    private MemeReprocessScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "minutesThreshold", 5);
        ReflectionTestUtils.setField(scheduler, "retryMaxDelayMs", 3600000L);
    }

    @Test
    void reenqueue_NoPendingMemes_DoesNothing() {
        when(moderationRepository.findPendingIdsOlderThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.reenqueuePendingMemes();

        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void reenqueue_RecentlyEnqueued_NotReEnqueued() {
        when(moderationRepository.findPendingIdsOlderThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.reenqueuePendingMemes();

        verify(aiProducer, never()).enqueueForProcessing(anyString());
    }

    @Test
    void reenqueue_StalePending_ReEnqueues() {
        when(moderationRepository.findPendingIdsOlderThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of("meme-1", "meme-2"));

        scheduler.reenqueuePendingMemes();

        verify(moderationRepository).resetToPendingIfProcessing("meme-1");
        verify(moderationRepository).resetToPendingIfProcessing("meme-2");
        verify(aiProducer).enqueueForProcessing("meme-1");
        verify(aiProducer).enqueueForProcessing("meme-2");
    }

    @Test
    void reenqueue_EnqueueError_DoesNotStopLoop() {
        when(moderationRepository.findPendingIdsOlderThan(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of("meme-1", "meme-2"));
        doThrow(new RuntimeException("rabbit down")).when(aiProducer).enqueueForProcessing("meme-1");

        scheduler.reenqueuePendingMemes();

        verify(moderationRepository).resetToPendingIfProcessing("meme-1");
        verify(moderationRepository).resetToPendingIfProcessing("meme-2");
        verify(aiProducer).enqueueForProcessing("meme-1");
        verify(aiProducer).enqueueForProcessing("meme-2");
    }
}
