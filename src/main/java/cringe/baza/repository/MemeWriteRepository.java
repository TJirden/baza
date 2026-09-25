package cringe.baza.repository;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import java.util.OptionalLong;

public interface MemeWriteRepository {

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

    /** Обновляет вектор и описание мема, не затрагивая визуальный хеш. */
    void updateMeme(String id, Meme meme);

    /** Удаляет векторное представление мема из индекса. */
    void delete(String id);

    /** Удаляет вектор из индекса и переводит запись БД в статус QUARANTINED. */
    void quarantine(String id);
}
