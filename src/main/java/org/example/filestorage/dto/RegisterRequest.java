package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username обязателен") String username,
        @NotBlank(message = "password обязателен")
        @Size(min = 6, message = "пароль должен быть не короче 6 символов") String password) {
}
