package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.UserMapper;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserRole;
import org.example.filestorage.model.UserStatus;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<UserDto> getById(Integer id) {
        return userRepository.findByIdAndStatus(id, UserStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new NotFoundException("User", id)))
                .map(userMapper::toDto)
                .doOnNext(dto -> log.debug("Найден юзер id={}", id));
    }

    @Override
    public Flux<UserDto> getAll() {
        return userRepository.findAllByStatus(UserStatus.ACTIVE)
                .map(userMapper::toDto)
                .doOnComplete(() -> log.debug("Отдан список юзеров"));
    }

    @Override
    public Mono<UserDto> create(String username, String rawPassword) {
        User user = new User(null, username, passwordEncoder.encode(rawPassword), UserRole.USER, UserStatus.ACTIVE);
        return userRepository.save(user)
                .map(userMapper::toDto)
                .doOnSuccess(dto -> log.info("Юзер '{}' создан, id={}", dto.username(), dto.id()))
                .doOnError(e -> log.error("Не удалось создать юзера '{}'", username, e));
    }

    @Override
    public Mono<UserDto> rename(Integer id, String username) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User", id)))
                .flatMap(user -> {
                    user.setUsername(username);
                    return userRepository.save(user);
                })
                .map(userMapper::toDto)
                .doOnSuccess(dto -> log.info("Юзер id={} переименован в '{}'", id, username))
                .doOnError(e -> !(e instanceof NotFoundException),
                        e -> log.error("Не удалось переименовать юзера id={}", id, e));
    }

    @Override
    public Mono<UserDto> delete(Integer id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User", id)))
                .flatMap(user -> {
                    user.setStatus(UserStatus.BLOCKED);
                    return userRepository.save(user);
                })
                .map(userMapper::toDto)
                .doOnSuccess(dto -> log.info("Юзер id={} заблокирован", id))
                .doOnError(e -> !(e instanceof NotFoundException),
                        e -> log.error("Не удалось заблокировать юзера id={}", id, e));
    }
}
