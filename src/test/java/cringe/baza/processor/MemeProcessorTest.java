package cringe.baza.processor;

import static org.mockito.Mockito.*;

import cringe.baza.model.IdRepository;
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
    void delete_CallsRepository() {
        String memeId = "meme-123";

        processor.delete(memeId);

        verify(idRepository).delete(memeId);
    }
}
