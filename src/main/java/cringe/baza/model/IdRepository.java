package cringe.baza.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface IdRepository {
    /** Сохраняет мем и (опционально) визуальный хеш атомарно в одной транзакции. */
    void save(String id, Meme meme, OptionalLong imageHash);

    /** Сохраняет мем без визуального хеша. */
    default void save(String id, Meme meme) {
        save(id, meme, OptionalLong.empty());
    }

    /** Ищет ID мемов, семантически близких к текстовому запросу с учетом прав доступа. */
    List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds);

    List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds);

    /** Удаляет векторное представление мема из индекса. */
    void delete(String id);

    /** Удаляет вектор из индекса и переводит запись БД в статус QUARANTINED. */
    void quarantine(String id);

    /** Возвращает визуальный хеш мема, если он сохранён. */
    default OptionalLong findImageHash(String id) {
        return OptionalLong.empty();
    }

    Optional<Meme> findById(String id);

    List<Meme> findAll(int limit, int offset);
}
