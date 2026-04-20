package cringe.baza.model;

import java.util.List;

public interface IdRepository {
    /**
     * Сохраняет описание мема в хранилище id.
     */
    void save(String id, String description, long chatId);

    /**
     * Ищет ID мемов, семантически близких к текстовому запросу.
     */
    List<String> findSimilarIds(String query, int limit);

    /**
     * Удаляет векторное представление мема из индекса.
     */
    void delete(String id);

    void clear();
}