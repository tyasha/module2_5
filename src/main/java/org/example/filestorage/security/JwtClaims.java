package org.example.filestorage.security;

import org.example.filestorage.model.UserRole;

public record JwtClaims(Integer userId, String username, UserRole role) {
}
