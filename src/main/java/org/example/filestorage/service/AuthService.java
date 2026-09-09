package org.example.filestorage.service;

import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<UserDto> register(String username, String password);

    Mono<TokenResponse> login(String username, String password);
}
