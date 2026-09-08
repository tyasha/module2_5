package org.example.filestorage.exception;

public class FileStorageUnavailableException extends RuntimeException {

    public FileStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
