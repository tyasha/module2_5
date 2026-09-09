package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.EventDto;
import org.example.filestorage.dto.FileDto;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;

class EventControllerTest extends AbstractIntegrationTest {

    @Test
    void getByIdWithoutTokenReturns401() {
        webTestClient.get().uri("/events/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void eventAppearsAfterUploadAndIsReadableThroughController() {
        webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("event-controller-user", "secret123"))
                .exchange()
                .expectStatus().isCreated();

        String token = webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("event-controller-user", "secret123"))
                .exchange()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .token();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource("content".getBytes()) {
            @Override
            public String getFilename() {
                return "doc.pdf";
            }
        });

        FileDto uploaded = webTestClient.post().uri("/files")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectBody(FileDto.class)
                .returnResult()
                .getResponseBody();

        webTestClient.get().uri("/events")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(EventDto.class)
                .value(events -> assertThat(events)
                        .anyMatch(e -> e.fileId().equals(uploaded.id())));
    }
}
