package ru.vkr.contracts.worker.generation;

public class PermanentGenerationException extends IllegalStateException {
    public PermanentGenerationException(String message) {
        super(message);
    }

    public PermanentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
