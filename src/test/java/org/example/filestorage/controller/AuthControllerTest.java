package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.exception.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest extends AbstractIntegrationTest {

    @Test
    void registerThenLoginReturnsValidToken() {
        UserDto registered = webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("newuser", "secret123"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(registered).isNotNull();
        assertThat(registered.username()).isEqualTo("newuser");

        TokenResponse token = webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("newuser", "secret123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(token).isNotNull();
        assertThat(token.token()).isNotBlank();
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("someuser", "correctpass"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("someuser", "wrongpass"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void registerWithBlankUsernameReturns400() {
        ErrorResponse error = webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("", "secret123"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.error()).contains("username");
    }

    @Test
    void registerWithDuplicateUsernameReturns409() {
        webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("dupeuser", "secret123"))
                .exchange()
                .expectStatus().isCreated();

        ErrorResponse error = webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("dupeuser", "otherpass"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.status()).isEqualTo(409);
        assertThat(error.error()).isNotBlank();
    }

    @Test
    void loginWithMalformedJsonBodyReturns400() {
        ErrorResponse error = webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not valid json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.error()).isNotBlank();
    }
}
