package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameRequest(@NotBlank(message = "username обязателен") String username) {
}
