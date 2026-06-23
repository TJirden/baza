package cringe.baza.bot.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeCleanupSchedulerTest {

    @Mock
    private MemeModerationRepository repository;

    @InjectMocks
    private MemeCleanupScheduler scheduler;

    @Test
    void cleanExpiredQuarantineMemes_NoExpiredMemes() {
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.QUARANTINED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.cleanExpiredQuarantineMemes();

        verify(repository, never()).delete(any(MemeModeration.class));
    }

    @Test
    void cleanExpiredQuarantineMemes_WithExpiredMemes() {
        MemeModeration meme1 = new MemeModeration(
                "meme-1",
                "file-1",
                "Desc 1",
                "",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.QUARANTINED,
                "Censorship");
        MemeModeration meme2 = new MemeModeration(
                "meme-2",
                "file-2",
                "Desc 2",
                "",
                222L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.QUARANTINED,
                "Duplicate");

        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.QUARANTINED), any(LocalDateTime.class)))
                .thenReturn(List.of(meme1, meme2));

        scheduler.cleanExpiredQuarantineMemes();

        verify(repository).delete(meme1);
        verify(repository).delete(meme2);
    }
}
