package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.security.JwtTokenProvider;
import org.example.filestorage.service.AuthService;
import org.example.filestorage.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(UserService userService, UserRepository userRepository,
                            PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<UserDto> register(String username, String password) {
        return userService.create(username, password);
    }

    @Override
    public Mono<TokenResponse> login(String username, String password) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Неверный логин или пароль")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new BadCredentialsException("Неверный логин или пароль"));
                    }
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
                    return Mono.just(new TokenResponse(token));
                })
                .doOnSuccess(dto -> log.info("Юзер '{}' залогинился", username))
                .doOnError(e -> !(e instanceof BadCredentialsException),
                        e -> log.error("Ошибка при логине юзера '{}'", username, e));
    }
}
