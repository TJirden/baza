package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.processor.MemeProcessor;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
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

    private MemeModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new MemeModerationService(
                mock(com.pengrad.telegrambot.TelegramBot.class), repository, memeProcessor, memeReportRepository, 3);
    }

    @Test
    void reportMeme_AlreadyReported() {
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(true);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(1L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals("ALREADY_REPORTED", result.status());
        assertEquals(1L, result.currentReports());
        verify(memeReportRepository, never()).save(any());
    }

    @Test
    void reportMeme_ReportAdded() {
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(false);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(1L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals("REPORT_ADDED", result.status());
        assertEquals(1L, result.currentReports());
        verify(memeReportRepository).save(any());
        verify(memeProcessor, never()).quarantine(anyString());
    }

    @Test
    void reportMeme_Quarantined() {
        when(memeReportRepository.existsByMemeIdAndReporterUserId("meme-1", 111L))
                .thenReturn(false);
        when(memeReportRepository.countByMemeId("meme-1")).thenReturn(3L);

        MemeModerationService.ReportResult result = moderationService.reportMeme("meme-1", 111L);

        assertEquals("QUARANTINED", result.status());
        assertEquals(3L, result.currentReports());
        verify(memeReportRepository).save(any());
        verify(memeProcessor).quarantine("meme-1");
        verify(memeReportRepository).deleteByMemeId("meme-1");
    }
}
