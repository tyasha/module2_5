package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username обязателен") String username,
        @NotBlank(message = "password обязателен") String password) {
}
