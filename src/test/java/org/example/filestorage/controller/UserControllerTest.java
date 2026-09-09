package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.RenameRequest;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest extends AbstractIntegrationTest {

    @Test
    void getByIdWithoutTokenReturns401() {
        webTestClient.get().uri("/users/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void fullCycleGetRenameDelete() {
        UserDto registered = webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("target-user", "secret123"))
                .exchange()
                .expectBody(UserDto.class)
                .returnResult()
                .getResponseBody();

        String token = webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("target-user", "secret123"))
                .exchange()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .token();

        webTestClient.get().uri("/users/" + registered.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .value(dto -> assertThat(dto.username()).isEqualTo("target-user"));

        webTestClient.put().uri("/users/" + registered.id())
                .header("Authorization", "Bearer " + token)
                .bodyValue(new RenameRequest("renamed-user"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .value(dto -> assertThat(dto.username()).isEqualTo("renamed-user"));

        webTestClient.delete().uri("/users/" + registered.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/users/" + registered.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }
}
