package org.example.filestorage.service;

import org.example.filestorage.dto.UserDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<UserDto> getById(Integer id);

    Flux<UserDto> getAll();

    Mono<UserDto> create(String username, String rawPassword);

    Mono<UserDto> rename(Integer id, String username);

    Mono<UserDto> delete(Integer id);
}
