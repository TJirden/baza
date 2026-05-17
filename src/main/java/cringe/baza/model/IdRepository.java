package cringe.baza.model;

import java.util.List;
import java.util.Optional;

public interface IdRepository {
    /**
     * Сохраняет описание мема в хранилище id.
     */
    void save(String id, Meme meme);

    /**
     * Ищет ID мемов, семантически близких к текстовому запросу с учетом прав доступа.
     */
    List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds);

    List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds);

    /**
     * Удаляет векторное представление мема из индекса.
     */
    void delete(String id);

    Optional<Meme> findById(String id);
}