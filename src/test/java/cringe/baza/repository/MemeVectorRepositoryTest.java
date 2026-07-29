package cringe.baza.repository;

import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeModeration;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class MemeVectorRepositoryTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeImageHashRepository memeImageHashRepository;

    @InjectMocks
    private MemeVectorRepository repository;

    @Test
    void delete_Success() {
        String memeId = "meme-123";

        repository.delete(memeId);

        verify(vectorStore).delete(List.of(memeId));
        verify(memeModerationRepository).deleteById(memeId);
        verify(memeImageHashRepository).deleteByMemeId(memeId);
    }

    @Test
    void quarantine_KeepsImageHash() {
        String memeId = "meme-456";
        MemeModeration moderation = new MemeModeration();
        moderation.setId(memeId);
        when(memeModerationRepository.findById(memeId)).thenReturn(Optional.of(moderation));

        repository.quarantine(memeId);

        verify(vectorStore).delete(List.of(memeId));
        verify(memeImageHashRepository, never()).deleteByMemeId(memeId);
        verify(memeModerationRepository).save(moderation);
    }
}
