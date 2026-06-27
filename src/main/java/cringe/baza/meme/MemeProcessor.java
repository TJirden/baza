package cringe.baza.meme;

import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemeProcessor {

    private final IdRepository idRepository;

    public String save(Meme meme) {
        String id = meme.id() != null ? meme.id() : UUID.randomUUID().toString();
        Meme memeWithId = new Meme(
                id,
                meme.description(),
                meme.ocrText(),
                meme.fileId(),
                meme.ownerId(),
                meme.visibility(),
                meme.groupIds());
        idRepository.save(id, memeWithId);
        return id;
    }

    public List<Meme> getAll(int limit, int offset) {
        return idRepository.findAll(limit, offset);
    }

    public List<Meme> getMemesByDescription(String description, int limit, Long userId, List<Long> userGroupIds) {
        List<String> ids = idRepository.findSimilarIds(description, limit, userId, userGroupIds);

        return ids.stream()
                .map(idRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<String> getFileIdsByDescription(String description, int limit, Long userId, List<Long> userGroupIds) {
        return idRepository.findSimilarFileIds(description, limit, userId, userGroupIds);
    }

    public List<Meme> searchWithIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        return getMemesByDescription(query, limit, userId, userGroupIds);
    }

    public Optional<Meme> getSingleMemeByDescription(String description, Long userId, List<Long> userGroupIds) {
        List<String> ids = idRepository.findSimilarIds(description, 1, userId, userGroupIds);

        if (ids.isEmpty()) {
            return Optional.empty();
        }

        return idRepository.findById(ids.getFirst());
    }

    public Optional<Meme> getMemeById(String id) {
        return idRepository.findById(id);
    }

    @Transactional
    public boolean delete(String id) {
        idRepository.delete(id);
        return true;
    }

    public boolean quarantine(String id) {
        if (idRepository.findById(id).isPresent()) {
            idRepository.quarantine(id);
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
        delete(id);
        save(new Meme(
                id, newDescription, meme.ocrText(), meme.fileId(), meme.ownerId(), meme.visibility(), meme.groupIds()));
        return true;
    }
}
