package cringe.baza.repository;

import cringe.baza.model.Meme;

public interface MemeProcessingRepository {

    /** Атомарно переводит PENDING-запись в PROCESSING (или перезахватывает зависший PROCESSING). Возвращает false, если запись уже в финальном статусе или PROCESSING обрабатывается активным воркером. */
    boolean claimForProcessing(String id);

    /** Переводит существующую PENDING- или PROCESSING-запись в APPROVED: добавляет вектор и обновляет поля модерации. Возвращает false, если запись уже не PENDING/PROCESSING. */
    boolean promoteToApproved(String id, Meme meme);

    /** Условно переводит PENDING-запись в QUARANTINED. Возвращает false, если запись уже не PENDING. */
    boolean updateToQuarantinedIfPending(String id, String description, String ocrText, String reason);

    /** Атомарно инкрементит счётчик ретраев PROCESSING-записи, обновляет время постановки в очередь и сбрасывает processingStartedAt (сигнал «воркер завершил попытку, готов к перезахвату»). Возвращает 0, если запись уже не PROCESSING. */
    int incrementRetryCount(String id);

    /** Отмечает время постановки мема в очередь (для предотвращения повторного enqueue). */
    void markEnqueued(String id);
}
