package org.example.filestorage.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ServerWebInputException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNotFoundExceptionTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("File", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).contains("File");
    }

    @Test
    void mapsBadCredentialsTo401() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(new BadCredentialsException("bad creds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("bad creds");
        assertThat(response.getBody().status()).isEqualTo(401);
    }

    @Test
    void mapsFileStorageUnavailableTo503() {
        ResponseEntity<ErrorResponse> response =
                handler.handleStorageUnavailable(new FileStorageUnavailableException("MinIO лёг", new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error()).isEqualTo("MinIO лёг");
        assertThat(response.getBody().status()).isEqualTo(503);
    }

    @Test
    void mapsDataIntegrityViolationTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("FK violation", new RuntimeException("some root cause")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).contains("some root cause");
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void mapsServerWebInputExceptionTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadInput(new ServerWebInputException("невалидный JSON"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).contains("невалидный JSON");
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void mapsUnexpectedExceptionTo500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("секретная деталь стектрейса"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("Внутренняя ошибка сервера");
        assertThat(response.getBody().status()).isEqualTo(500);
    }
}
