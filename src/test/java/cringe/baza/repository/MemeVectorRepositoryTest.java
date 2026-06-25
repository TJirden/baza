package cringe.baza.repository;

import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeBattle;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeBattleVoteRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import cringe.baza.repository.jpa.MemeSwipeVoteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MemeVectorRepositoryTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeRatingRepository memeRatingRepository;

    @Mock
    private MemeReportRepository memeReportRepository;

    @Mock
    private MemeSwipeVoteRepository memeSwipeVoteRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private MemeBattleVoteRepository memeBattleVoteRepository;

    @InjectMocks
    private MemeVectorRepository repository;

    @Test
    void delete_CascadesCorrectly() {
        String memeId = "meme-123";
        MemeBattle battle = new MemeBattle();
        battle.setId(10L);
        when(memeRatingRepository.existsById(memeId)).thenReturn(true);
        when(memeBattleRepository.findReferencingMeme(memeId)).thenReturn(List.of(battle));

        repository.delete(memeId);

        verify(memeRatingRepository).deleteById(memeId);
        verify(memeReportRepository).deleteByMemeId(memeId);
        verify(memeSwipeVoteRepository).deleteByMemeId(memeId);
        verify(memeBattleVoteRepository).deleteByBattleId(10L);
        verify(memeBattleRepository).deleteAll(anyList());
        verify(vectorStore).delete(List.of(memeId));
        verify(memeModerationRepository).deleteById(memeId);
    }
}
