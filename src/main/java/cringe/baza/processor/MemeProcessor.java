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
        String id = meme.id() != null ? meme.id() : UUID.randomUUID().toString();
        Meme memeWithId = new Meme(id, meme.description(), meme.fileId(), meme.ownerId(), meme.visibility(), meme.groupIds());
        idRepository.save(id, memeWithId);
        return id;
    }

    public List<Meme> getAll(int limit, int offset) {
        if (idRepository instanceof cringe.baza.repository.MemeVectorRepository repo) {
            return repo.findAll(limit, offset).stream()
                    .map(row -> row.get("id").toString())
                    .map(idRepository::findById)
                    .flatMap(Optional::stream)
                    .toList();
        }
        return List.of();
    }

    /**
     * Поиск мемов по смыслу описания (семантический поиск)
     */
    public List<Meme> getMemesByDescription(String description, int limit, Long userId, List<Long> userGroupIds) {
        List<String> ids = idRepository.findSimilarIds(description, limit, userId, userGroupIds);

        return ids.stream()
                .map(idRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Поиск списка Telegram File ID по описанию для Inline Mode
     */
    public List<String> getFileIdsByDescription(String description, int limit, Long userId, List<Long> userGroupIds) {
        return idRepository.findSimilarFileIds(description, limit, userId, userGroupIds);
    }

    public List<Meme> searchWithIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        return getMemesByDescription(query, limit, userId, userGroupIds);
    }

    /**
     * Поиск одного наиболее подходящего мема по смыслу описания
     */
    public Optional<Meme> getSingleMemeByDescription(String description, Long userId, List<Long> userGroupIds) {
        List<String> ids = idRepository.findSimilarIds(description, 1, userId, userGroupIds);

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

    public boolean delete(String id) {
        if (idRepository.findById(id).isPresent()) {
            idRepository.delete(id);
            return true;
        }
        return false;
    }

    public boolean update(String id, String newDescription) {
        Optional<Meme> memeOpt = idRepository.findById(id);
        if (memeOpt.isEmpty()) {
            return false;
        }

        Meme meme = memeOpt.get();
        idRepository.delete(id);
        idRepository.save(id, new Meme(id, newDescription, meme.fileId(), meme.ownerId(), meme.visibility(), meme.groupIds()));
        return true;
    }
}
