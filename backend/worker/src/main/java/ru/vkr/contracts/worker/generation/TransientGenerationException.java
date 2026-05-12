package ru.vkr.contracts.worker.generation;

public class TransientGenerationException extends IllegalStateException {
    public TransientGenerationException(String message) {
        super(message);
    }

    public TransientGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
