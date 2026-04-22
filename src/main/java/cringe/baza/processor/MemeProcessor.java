package cringe.baza.processor;

import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemeProcessor {

    private final MemeRepository memeRepository;
    private final IdRepository idRepository;

    /**
     * @return id мема
     */
    public String save(Meme meme){
        String id = UUID.randomUUID().toString();
        idRepository.save(id, meme.description(), meme.fileId());
        try {
            memeRepository.put(id, meme);
        } catch (Exception e) {
            idRepository.delete(id);
            throw e;
        }
        return id;
    }

    /**
     * Поиск мемов по смыслу описания (семантический поиск)
     */
    public List<Meme> getMemesByDescription(String description, int limit) {
        List<String> ids = idRepository.findSimilarIds(description, limit);

        return ids.stream()
                .map(memeRepository::get)
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

        return memeRepository.get(ids.getFirst());
    }

    /**
     * Получение конкретного мема по его ID
     */
    public Optional<Meme> getMemeById(String id) {
        return memeRepository.get(id);
    }

    /**
     * @return количество удаленных мемов
     */
    public int clearAllData() {
        idRepository.clear();
        return memeRepository.clear();
    }

}
