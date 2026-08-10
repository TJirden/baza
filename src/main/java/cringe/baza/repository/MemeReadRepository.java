package cringe.baza.repository;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import java.util.List;
import java.util.Optional;

public interface MemeReadRepository {

    /** Загружает запись модерации по ID независимо от статуса (для consumer'а очереди). */
    Optional<MemeModeration> findModerationById(String id);

    /** Находит одобренный визуальный дубликат мема по его ID (исключая сам мем). Пусто, если дублей нет. */
    Optional<String> findApprovedDuplicate(String id, int phashThreshold);

    /** Ищет ID мемов, семантически близких к текстовому запросу с учетом прав доступа. */
    List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds);

    List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds);

    Optional<Meme> findById(String id);

    List<Meme> findAll(int limit, int offset);
}
