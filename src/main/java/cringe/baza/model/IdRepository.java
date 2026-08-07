package cringe.baza.model;

import cringe.baza.domain.MemeModeration;
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

    /** Сохраняет запись модерации (например, в карантин) и (опционально) визуальный хеш. */
    void saveQuarantined(MemeModeration moderation, OptionalLong imageHash);

    /** Сохраняет запись модерации в статусе PENDING и (опционально) визуальный хеш. */
    void savePending(MemeModeration moderation, OptionalLong imageHash);

    /** Атомарно переводит PENDING-запись в PROCESSING (или перезахватывает зависший PROCESSING). Возвращает false, если запись уже в финальном статусе или PROCESSING обрабатывается активным воркером. */
    boolean claimForProcessing(String id);

    /** Переводит существующую PENDING- или PROCESSING-запись в APPROVED: добавляет вектор и обновляет поля модерации. Возвращает false, если запись уже не PENDING/PROCESSING. */
    boolean promoteToApproved(String id, Meme meme);

    /** Условно переводит PENDING-запись в QUARANTINED. Возвращает false, если запись уже не PENDING. */
    boolean updateToQuarantinedIfPending(String id, String description, String ocrText, String reason);

    /** Находит одобренный визуальный дубликат мема по его ID (исключая сам мем). Пусто, если дублей нет. */
    Optional<String> findApprovedDuplicate(String id, int phashThreshold);

    /** Атомарно инкрементит счётчик ретраев PROCESSING-записи, обновляет время постановки в очередь и сбрасывает processingStartedAt (сигнал «воркер завершил попытку, готов к перезахвату»). Возвращает 0, если запись уже не PROCESSING. */
    int incrementRetryCount(String id);

    /** Отмечает время постановки мема в очередь (для предотвращения повторного enqueue). */
    void markEnqueued(String id);

    /** Загружает запись модерации по ID независимо от статуса (для consumer'а очереди). */
    Optional<MemeModeration> findModerationById(String id);

    /** Обновляет вектор и описание мема, не затрагивая визуальный хеш. */
    void updateMeme(String id, Meme meme);

    /** Ищет ID мемов, семантически близких к текстовому запросу с учетом прав доступа. */
    List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds);

    List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds);

    /** Удаляет векторное представление мема из индекса. */
    void delete(String id);

    /** Удаляет вектор из индекса и переводит запись БД в статус QUARANTINED. */
    void quarantine(String id);

    Optional<Meme> findById(String id);

    List<Meme> findAll(int limit, int offset);
}
