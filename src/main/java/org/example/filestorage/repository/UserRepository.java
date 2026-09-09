package org.example.filestorage.repository;

import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Integer> {

    Mono<User> findByIdAndStatus(Integer id, UserStatus status);

    Flux<User> findAllByStatus(UserStatus status);

    Mono<User> findByUsername(String username);
}
