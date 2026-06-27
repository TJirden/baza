package cringe.baza.meme;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeProcessorTest {

    @Mock
    private IdRepository idRepository;

    @InjectMocks
    private MemeProcessor processor;

    @Test
    void save_WithMemeId_UsesExistingId() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());

        String savedId = processor.save(meme);

        assertEquals("meme-123", savedId);
        verify(idRepository).save(eq("meme-123"), any(Meme.class));
    }

    @Test
    void save_WithoutMemeId_GeneratesNewId() {
        Meme meme = new Meme(null, "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());

        String savedId = processor.save(meme);

        assertNotNull(savedId);
        assertFalse(savedId.isEmpty());
        verify(idRepository).save(eq(savedId), any(Meme.class));
    }

    @Test
    void getAll() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findAll(10, 5)).thenReturn(List.of(meme));

        List<Meme> result = processor.getAll(10, 5);

        assertEquals(1, result.size());
        assertEquals("meme-123", result.getFirst().id());
    }

    @Test
    void getMemesByDescription() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findSimilarIds("test", 5, 1L, List.of(10L))).thenReturn(List.of("meme-123", "meme-456"));
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(meme));
        when(idRepository.findById("meme-456")).thenReturn(Optional.empty());

        List<Meme> result = processor.getMemesByDescription("test", 5, 1L, List.of(10L));

        assertEquals(1, result.size());
        assertEquals("meme-123", result.getFirst().id());
    }

    @Test
    void getFileIdsByDescription() {
        when(idRepository.findSimilarFileIds("test", 5, 1L, List.of(10L))).thenReturn(List.of("file-1", "file-2"));

        List<String> result = processor.getFileIdsByDescription("test", 5, 1L, List.of(10L));

        assertEquals(List.of("file-1", "file-2"), result);
    }

    @Test
    void searchWithIds() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findSimilarIds("test", 5, 1L, List.of(10L))).thenReturn(List.of("meme-123"));
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(meme));

        List<Meme> result = processor.searchWithIds("test", 5, 1L, List.of(10L));

        assertEquals(1, result.size());
        assertEquals("meme-123", result.getFirst().id());
    }

    @Test
    void getSingleMemeByDescription_Found() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findSimilarIds("test", 1, 1L, List.of(10L))).thenReturn(List.of("meme-123"));
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(meme));

        Optional<Meme> result = processor.getSingleMemeByDescription("test", 1L, List.of(10L));

        assertTrue(result.isPresent());
        assertEquals("meme-123", result.get().id());
    }

    @Test
    void getSingleMemeByDescription_NotFound() {
        when(idRepository.findSimilarIds("test", 1, 1L, List.of(10L))).thenReturn(List.of());

        Optional<Meme> result = processor.getSingleMemeByDescription("test", 1L, List.of(10L));

        assertTrue(result.isEmpty());
    }

    @Test
    void getMemeById() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(meme));

        Optional<Meme> result = processor.getMemeById("meme-123");

        assertTrue(result.isPresent());
        assertEquals("meme-123", result.get().id());
    }

    @Test
    void delete_CallsRepository() {
        String memeId = "meme-123";

        processor.delete(memeId);

        verify(idRepository).delete(memeId);
    }

    @Test
    void quarantine_Found() {
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(mock(Meme.class)));

        boolean result = processor.quarantine("meme-123");

        assertTrue(result);
        verify(idRepository).quarantine("meme-123");
    }

    @Test
    void quarantine_NotFound() {
        when(idRepository.findById("meme-123")).thenReturn(Optional.empty());

        boolean result = processor.quarantine("meme-123");

        assertFalse(result);
        verify(idRepository, never()).quarantine("meme-123");
    }

    @Test
    void update_Found() {
        Meme meme = new Meme("meme-123", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(idRepository.findById("meme-123")).thenReturn(Optional.of(meme));

        boolean result = processor.update("meme-123", "new description");

        assertTrue(result);
        verify(idRepository).delete("meme-123");
        verify(idRepository).save(eq("meme-123"), argThat(m -> "new description".equals(m.description())));
    }

    @Test
    void update_NotFound() {
        when(idRepository.findById("meme-123")).thenReturn(Optional.empty());

        boolean result = processor.update("meme-123", "new description");

        assertFalse(result);
        verify(idRepository, never()).delete(anyString());
        verify(idRepository, never()).save(anyString(), any(Meme.class));
    }
}
