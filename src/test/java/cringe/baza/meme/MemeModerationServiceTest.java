package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeImageHash;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.ReportStatus;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeModerationServiceTest {

    @Mock
    private MemeModerationRepository repository;

    @Mock
    private MemeProcessor memeProcessor;

    @Mock
    private MemeReportRepository memeReportRepository;

    @Mock
    private MemeImageHashRepository memeImageHashRepository;

    @Mock
    private TelegramService telegramService;

    private MemeModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new MemeModerationService(
                telegramService, repository, memeProcessor, memeReportRepository, memeImageHashRepository, 3, 5);
    }

    @Test
    void reportMeme_AlreadyReported() {
        when(repository.existsById("meme-1")).thenReturn(true);
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(true);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(1L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals(ReportStatus.ALREADY_REPORTED, result.status());
        assertEquals(1L, result.currentReports());
        verify(memeReportRepository, never()).save(any());
    }

    @Test
    void reportMeme_ReportAdded() {
        when(repository.existsById("meme-1")).thenReturn(true);
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(false);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(1L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals(ReportStatus.REPORT_ADDED, result.status());
        assertEquals(1L, result.currentReports());
        verify(memeReportRepository).save(any());
        verify(memeProcessor, never()).quarantine(anyString());
    }

    @Test
    void reportMeme_Quarantined() {
        when(repository.existsById("meme-1")).thenReturn(true);
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(false);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(3L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals(ReportStatus.QUARANTINED, result.status());
        assertEquals(3L, result.currentReports());
        verify(memeReportRepository).save(any());
        verify(memeProcessor).quarantine("meme-1");
        verify(memeReportRepository).deleteByMemeId("meme-1");
    }

    @Test
    void reportMeme_NotFound() {
        when(repository.existsById("meme-1")).thenReturn(false);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals(ReportStatus.NOT_FOUND, result.status());
        assertEquals(0L, result.currentReports());
        verify(memeReportRepository, never()).save(any());
    }

    @Test
    void approveMeme_NotFound() {
        when(repository.findById("meme-1")).thenReturn(Optional.empty());
        MemeModerationService.ApprovalResult result = moderationService.approveMeme("meme-1");
        assertInstanceOf(MemeModerationService.ApprovalResult.NotFound.class, result);
    }

    @Test
    void approveMeme_AlreadyApproved() {
        MemeModeration meme = new MemeModeration();
        meme.setId("meme-1");
        meme.setStatus(cringe.baza.model.ModerationStatus.APPROVED);

        when(repository.findById("meme-1")).thenReturn(Optional.of(meme));

        MemeModerationService.ApprovalResult result = moderationService.approveMeme("meme-1");
        assertInstanceOf(MemeModerationService.ApprovalResult.Approved.class, result);
        verify(memeProcessor, never()).save(any());
        verify(memeImageHashRepository, never()).findById(anyString());
    }

    @Test
    void approveMeme_Success() {
        MemeModeration meme = new MemeModeration();
        meme.setId("meme-1");
        meme.setStatus(cringe.baza.model.ModerationStatus.PENDING);
        meme.setDescription("Test description");
        meme.setOcrText("OCR");
        meme.setFileId("file-1");
        meme.setOwnerId(123L);
        meme.setVisibility(cringe.baza.model.MemeVisibility.PUBLIC);

        when(repository.findById("meme-1")).thenReturn(Optional.of(meme));
        when(memeImageHashRepository.findById("meme-1")).thenReturn(Optional.empty());

        MemeModerationService.ApprovalResult result = moderationService.approveMeme("meme-1");
        assertInstanceOf(MemeModerationService.ApprovalResult.Approved.class, result);

        verify(memeProcessor).save(any());
        assertEquals(cringe.baza.model.ModerationStatus.APPROVED, meme.getStatus());
        verify(repository).save(meme);
        verify(telegramService).sendMessageWithMarkdown(eq(123L), anyString());
    }

    @Test
    void approveMeme_DuplicateBlocked() {
        MemeModeration meme = new MemeModeration();
        meme.setId("meme-1");
        meme.setStatus(cringe.baza.model.ModerationStatus.QUARANTINED);
        meme.setDescription("Duplicate");
        meme.setOwnerId(123L);
        meme.setVisibility(cringe.baza.model.MemeVisibility.PUBLIC);

        MemeImageHash hash = new MemeImageHash("meme-1", 0xDEADBEEFL);

        when(repository.findById("meme-1")).thenReturn(Optional.of(meme));
        when(memeImageHashRepository.findById("meme-1")).thenReturn(Optional.of(hash));
        when(memeImageHashRepository.findNearestApprovedExcluding(0xDEADBEEFL, 5, "meme-1"))
                .thenReturn(Optional.of("approved-original"));

        MemeModerationService.ApprovalResult result = moderationService.approveMeme("meme-1");
        assertInstanceOf(MemeModerationService.ApprovalResult.DuplicateBlocked.class, result);
        assertEquals(
                "approved-original", ((MemeModerationService.ApprovalResult.DuplicateBlocked) result).duplicateOf());

        verify(memeProcessor, never()).save(any());
        assertEquals(cringe.baza.model.ModerationStatus.QUARANTINED, meme.getStatus());
        assertEquals("Визуальный дубликат одобренного мема: approved-original", meme.getModerationReason());
        verify(repository).save(meme);
        verify(telegramService, never()).sendMessageWithMarkdown(anyLong(), anyString());
    }

    @Test
    void rejectMeme_NotFound() {
        when(repository.findById("meme-1")).thenReturn(java.util.Optional.empty());
        boolean result = moderationService.rejectMeme("meme-1", "reason");
        assertFalse(result);
    }

    @Test
    void rejectMeme_Success() {
        MemeModeration meme = new MemeModeration();
        meme.setId("meme-1");
        meme.setOwnerId(123L);

        when(repository.findById("meme-1")).thenReturn(java.util.Optional.of(meme));

        boolean result = moderationService.rejectMeme("meme-1", "bad quality");
        assertTrue(result);

        verify(memeProcessor).delete("meme-1");
        verify(repository).delete(meme);
        verify(telegramService).sendMessageWithMarkdown(eq(123L), contains("bad quality"));
    }
}
