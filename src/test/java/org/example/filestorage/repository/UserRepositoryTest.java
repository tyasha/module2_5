package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserRole;
import org.example.filestorage.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserById() {
        User user = new User(null, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE);

        User saved = userRepository.save(user).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        StepVerifier.create(userRepository.findById(saved.getId()))
                .expectNextMatches(found ->
                        found.getUsername().equals("ivan") && found.getStatus() == UserStatus.ACTIVE)
                .verifyComplete();
    }

    @Test
    void findByIdAndStatusExcludesUserWithDifferentStatus() {
        User user = new User(null, "blocked-user", "password123", UserRole.USER, UserStatus.BLOCKED);
        User saved = userRepository.save(user).block();

        StepVerifier.create(userRepository.findByIdAndStatus(saved.getId(), UserStatus.ACTIVE))
                .verifyComplete();

        StepVerifier.create(userRepository.findAllByStatus(UserStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void findByUsernameReturnsUser() {
        User saved = userRepository.save(new User(null, "unique-login", "password123", UserRole.USER, UserStatus.ACTIVE)).block();

        StepVerifier.create(userRepository.findByUsername("unique-login"))
                .expectNextMatches(found -> found.getId().equals(saved.getId()))
                .verifyComplete();
    }

    @Test
    void findByUsernameReturnsEmptyWhenNotFound() {
        StepVerifier.create(userRepository.findByUsername("does-not-exist"))
                .verifyComplete();
    }
}
