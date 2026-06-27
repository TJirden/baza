package cringe.baza.bot.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeDigestSchedulerTest {

    @Mock
    private MemeDigestService digestService;

    @InjectMocks
    private MemeDigestScheduler scheduler;

    @Test
    void runDigestJob_CallsService() {
        scheduler.runDigestJob();
        verify(digestService).runAllGroupDigests();
    }

    @Test
    void runDigestJob_HandlesException() {
        doThrow(new RuntimeException("Error occurred")).when(digestService).runAllGroupDigests();

        // Should not propagate the exception
        scheduler.runDigestJob();

        verify(digestService).runAllGroupDigests();
    }
}
