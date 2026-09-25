package cringe.baza.repository;

/**
 * Композитный интерфейс, объединяющий read/write/processing-операции над мемами.
 * <p>
 * Потребители, которым нужны методы из нескольких групп, могут зависеть от этого интерфейса.
 * Узкие потребители (scheduler, producer) должны зависеть от одного из:
 * {@link MemeReadRepository}, {@link MemeWriteRepository}, {@link MemeProcessingRepository}.
 */
public interface IdRepository extends MemeReadRepository, MemeWriteRepository, MemeProcessingRepository {}
