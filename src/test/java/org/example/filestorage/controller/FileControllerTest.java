package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
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

class FileControllerTest extends AbstractIntegrationTest {

    private String registerAndLogin(String username) {
        webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest(username, "secret123"))
                .exchange()
                .expectStatus().isCreated();

        return webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest(username, "secret123"))
                .exchange()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .token();
    }

    @Test
    void uploadGetMetadataDownloadAndDelete() {
        String token = registerAndLogin("file-controller-user");

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource("hello controller".getBytes()) {
            @Override
            public String getFilename() {
                return "report.pdf";
            }
        });

        FileDto uploaded = webTestClient.post().uri("/files")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(FileDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(uploaded).isNotNull();
        assertThat(uploaded.name()).isEqualTo("report.pdf");

        webTestClient.get().uri("/files/" + uploaded.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FileDto.class)
                .value(dto -> assertThat(dto.name()).isEqualTo("report.pdf"));

        byte[] downloaded = webTestClient.get().uri("/files/" + uploaded.id() + "/download")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/octet-stream")
                .expectHeader().value("Content-Disposition", value -> assertThat(value).contains("report.pdf"))
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(new String(downloaded)).isEqualTo("hello controller");

        webTestClient.delete().uri("/files/" + uploaded.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/files/" + uploaded.id())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void uploadWithoutTokenReturns401() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource("content".getBytes()) {
            @Override
            public String getFilename() {
                return "doc.pdf";
            }
        });

        webTestClient.post().uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
