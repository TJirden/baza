package cringe.baza.meme;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
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

    @Mock
    private IdRepository idRepository;

    @InjectMocks
    private MemeCleanupScheduler scheduler;

    @Test
    void cleanExpiredMemes_NoExpiredMemes() {
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.QUARANTINED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.cleanExpiredMemes();

        verify(idRepository, never()).delete(anyString());
    }

    @Test
    void cleanExpiredMemes_WithExpiredQuarantineMemes() {
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
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.cleanExpiredMemes();

        verify(idRepository).delete("meme-1");
        verify(idRepository).delete("meme-2");
    }

    @Test
    void cleanExpiredMemes_WithExpiredPendingMemes() {
        MemeModeration pending = new MemeModeration(
                "meme-pending",
                "file-1",
                "Desc",
                "",
                111L,
                MemeVisibility.PUBLIC,
                "",
                ModerationStatus.PENDING,
                "Ожидает");

        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.QUARANTINED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(pending));

        scheduler.cleanExpiredMemes();

        verify(idRepository).delete("meme-pending");
    }

    @Test
    void cleanExpiredMemes_DeleteFailure_DoesNotStopLoop() {
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
        when(repository.findByStatusAndCreatedAtBefore(eq(ModerationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("db error")).when(idRepository).delete("meme-1");

        scheduler.cleanExpiredMemes();

        verify(idRepository).delete("meme-1");
        verify(idRepository).delete("meme-2");
    }
}
