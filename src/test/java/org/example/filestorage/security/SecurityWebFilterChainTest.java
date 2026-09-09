package org.example.filestorage.security;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityWebFilterChainTest extends AbstractIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void requestWithoutTokenToProtectedPathGets401() {
        webTestClient.get().uri("/some-random-nonexistent-path")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void requestWithValidTokenPassesAuthentication() {
        String token = jwtTokenProvider.generateToken(1, "ivan", UserRole.USER);

        webTestClient.get().uri("/some-random-nonexistent-path")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void requestWithInvalidTokenGets401WithJsonBody() {
        webTestClient.get().uri("/some-random-nonexistent-path")
                .header("Authorization", "Bearer this-is-not-a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void authPathsAreOpenWithoutToken() {
        webTestClient.post().uri("/auth/login")
                .bodyValue("{}")
                .exchange()
                .expectStatus().is4xxClientError()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }
}
