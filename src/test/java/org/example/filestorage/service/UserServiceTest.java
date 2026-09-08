package org.example.filestorage.service;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.UserMapper;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        userService = new UserServiceImpl(userRepository, userMapper);
    }

    @Test
    void createsUserWithActiveStatus() {
        User saved = new User(1, "ivan", UserStatus.ACTIVE);
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.create("ivan"))
                .expectNextMatches(dto -> dto.id().equals(1)
                        && dto.username().equals("ivan")
                        && dto.status() == UserStatus.ACTIVE)
                .verifyComplete();

        verify(userRepository).save(argThat(u ->
                u.getId() == null && u.getUsername().equals("ivan") && u.getStatus() == UserStatus.ACTIVE));
    }

    @Test
    void deleteSetsStatusToBlockedWithoutRemovingRow() {
        User existing = new User(1, "ivan", UserStatus.ACTIVE);
        User blocked = new User(1, "ivan", UserStatus.BLOCKED);
        when(userRepository.findById(1)).thenReturn(Mono.just(existing));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(blocked));

        StepVerifier.create(userService.delete(1))
                .expectNextMatches(dto -> dto.status() == UserStatus.BLOCKED)
                .verifyComplete();

        verify(userRepository, never()).deleteById(eq(1));
        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.BLOCKED));
    }

    @Test
    void getByIdThrowsNotFoundWhenUserDoesNotExist() {
        when(userRepository.findByIdAndStatus(1, UserStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(userService.getById(1))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void renameUpdatesUsername() {
        User existing = new User(1, "ivan", UserStatus.ACTIVE);
        User renamed = new User(1, "ivan2", UserStatus.ACTIVE);
        when(userRepository.findById(1)).thenReturn(Mono.just(existing));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(renamed));

        StepVerifier.create(userService.rename(1, "ivan2"))
                .expectNextMatches(dto -> dto.username().equals("ivan2"))
                .verifyComplete();

        verify(userRepository).save(argThat(u -> u.getUsername().equals("ivan2")));
    }
}
