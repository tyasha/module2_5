package org.example.filestorage.dto;

import org.example.filestorage.model.UserStatus;

public record UserDto(Integer id, String username, UserStatus status) {
}
