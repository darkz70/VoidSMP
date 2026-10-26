package ru.voidsmp.spawnrates.storage;

/**
 * Любая ошибка хранилища наружу. Проверяемые исключения не используем:
 * вызывающий код — async-поток записи, ему всё равно нечего сделать, кроме
 * как залогировать и продолжить игру без сохранения.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
