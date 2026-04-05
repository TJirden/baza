package cringe.baza.bot.imaginator;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

public interface ImageManager {

    /**
     * Сохраняет картинку
     * @param image сама картинка
     * @param description описание картинки
     * @param chatId ID чата пользователя
     * @return уникальный идентификатор сохраненной картинки
     */
    String saveImage(BufferedImage image, String description, Long chatId);

    /**
     * Достает мем по ID
     * @param id уникальный идентификатор мема
     * @return Optional с мемом
     */
    Optional<Meme> getMeme(String id);

    /**
     * Достает все мемы пользователя
     * @param chatId ID чата пользователя
     * @return список мемов пользователя
     */
    List<Meme> getUserMemes(Long chatId);

    /**
     * Достает последние N мемов пользователя
     * @param chatId ID чата пользователя
     * @param limit количество мемов
     * @return список последних мемов
     */
    List<Meme> getRecentUserMemes(Long chatId, int limit);

    /**
     * Удаляет мем по ID
     * @param id уникальный идентификатор мема
     * @return true если удаление успешно
     */
    boolean deleteMeme(String id);

    /**
     * Обновляет описание мема
     * @param id уникальный идентификатор мема
     * @param newDescription новое описание
     * @return true если обновление успешно
     */
    boolean updateDescription(String id, String newDescription);
}