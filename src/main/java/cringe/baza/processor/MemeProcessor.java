package cringe.baza.processor;

import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemeProcessor {

    private final IdRepository idRepository;

    /**
     * @return id мема
     */
    public String save(Meme meme){
        String id = UUID.randomUUID().toString();
        idRepository.save(id, meme.description(), meme.fileId());
        return id;
    }

    /**
     * Поиск мемов по смыслу описания (семантический поиск)
     */
    public List<Meme> getMemesByDescription(String description, int limit) {
        List<String> ids = idRepository.findSimilarIds(description, limit);

        return ids.stream()
                .map(idRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Поиск списка Telegram File ID по описанию для Inline Mode
     */
    public List<String> getFileIdsByDescription(String description, int limit) {
        return idRepository.findSimilarFileIds(description, limit);
    }

    /**
     * Поиск одного наиболее подходящего мема по смыслу описания
     */
    public Optional<Meme> getSingleMemeByDescription(String description) {
        List<String> ids = idRepository.findSimilarIds(description, 1);

        if (ids.isEmpty()) {
            return Optional.empty();
        }

        return idRepository.findById(ids.getFirst());
    }

    /**
     * Получение конкретного мема по его ID
     */
    public Optional<Meme> getMemeById(String id) {
        return idRepository.findById(id);
    }

    /**
     * @return количество удаленных мемов
     */
    public int clearAllData() {
        idRepository.clear();
        return 1;
    }

}
