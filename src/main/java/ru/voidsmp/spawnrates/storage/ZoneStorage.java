package ru.voidsmp.spawnrates.storage;

/**
 * Абстракция хранилища зон. Все методы вызываются из единственного
 * потока-писателя (см. {@link PersistenceService}), поэтому реализации
 * достаточно сделать потокобезопасными «на всякий случай».
 */
public interface ZoneStorage {

    /** Читает все зоны. Пустой список — если хранилище ещё пустое. */
    java.util.List<ZoneRecord> loadAll();

    /** Вставляет или обновляет одну зону (по уникальному имени). */
    void save(ZoneRecord record);

    /** Удаляет зону по имени. Молча делает вид, что всё вышло, если имени нет. */
    void delete(String name);

    void saveAll(java.util.Collection<ZoneRecord> records);

    /** Синхронизация с диском (WAL checkpoint и прочее). */
    void checkpoint();

    void close();

    /** Человекочитаемое описание для /spawnrate info и лога при старте. */
    String describe();
}
