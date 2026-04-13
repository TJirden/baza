package cringe.baza.model;

import cringe.baza.model.Meme;

import java.util.Optional;

public interface MemeRepository {
    /**
     * Сохраняет мем по id
     * @param id - id по котором будет сохранен мем
     * @param meme - мем
     */
    void put(String id, Meme meme);

    /**
     * Получаем мем по id
     * @param id - id мема
     * @return null если мема с таким id нет, иначе возвращается мем
     */
    Optional<Meme> get(String id);

    /**
     * Удаляет мем по id
     * @param id - id мема
     * @return удаляет мем и возвращает его, если мема не было, то возвращается null
     */
    Optional<Meme> remove(String id);

    /**
     * @return количество мемов в хранилище
     */
    int size();

    /**
     * Удаляет все мемы
     * @return возвращает количество удаленых мемов
     */
    int clear();
}