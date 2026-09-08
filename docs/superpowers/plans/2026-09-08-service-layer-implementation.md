# Сервисный слой Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать сервисный слой практики (MinIO-клиент, upload-flow, CRUD-сервисы для User/File/Event) по дизайн-документу `docs/superpowers/specs/2026-09-08-service-layer-design.md`.

**Architecture:** Реактивные сервисы поверх уже готовых репозиториев (R2DBC) и MapStruct-мапперов. Upload — оркестрация MinIO (сначала) → БД (`File`+`Event` одной транзакцией). Ошибки MinIO оборачиваются в собственное исключение. Роль/авторизация — вне рамок, сервисы читают без фильтрации.

**Поправка по факту реализации** (оригинальные строки ниже намеренно не переписаны — честная история "предполагали / оказалось"): координаты Testcontainers-MinIO и пакет класса, упомянутые ниже по тексту как `org.testcontainers:minio`/`org.testcontainers.minio.MinIOContainer`, подтверждённо неверны. Реально работает: зависимость `org.testcontainers:testcontainers-minio`, класс `org.testcontainers.containers.MinIOContainer` (модуль MinIO не мигрировал на новую раскладку пакетов Testcontainers 2.x, в отличие от MySQL). Уже применено в коде (см. `AbstractIntegrationTest`).

**Tech Stack:** Spring Boot 4.1.1, WebFlux, Spring Data R2DBC, AWS SDK v2 (`S3AsyncClient`), Testcontainers (MySQL + MinIO), JUnit 5, Mockito, Reactor Test.

## Global Constraints

- `location` (`File`) — только UUID-ключ объекта, никогда не полный URL, никогда не в DTO
- Upload-flow: сначала MinIO (`put`), затем одной DB-транзакцией `File`+`Event`
- `userId` в `FileService.upload` — явный параметр (не retriever), это данные, не авторизация
- Роль/авторизация НЕ реализуется в этом плане — `getById`/`getAll` без фильтрации по ролям
- `User`/`File` — soft delete (`status → BLOCKED`/`ARCHIVED`), `Event` — honest hard delete
- Ошибки `S3AsyncClient` оборачиваются в `FileStorageUnavailableException` (unchecked), не пробрасываются как есть
- MinIO-клиент асинхронный (`S3AsyncClient`), синхронного HTTP-клиента в проекте нет и не появляется
- Пакеты — `service`/`service/impl` (интерфейс + реализация раздельно, как в 2.4), плоско
- Сервисы возвращают DTO (`UserDto`/`FileDto`/`EventDto`), не entity — маппинг через уже готовые MapStruct-мапперы

---

## Task 1: UserService — CRUD, soft delete

**Files:**
- Create: `src/main/java/org/example/filestorage/service/UserService.java`
- Create: `src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java`
- Test: `src/test/java/org/example/filestorage/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (уже есть), `UserMapper` (уже есть, MapStruct), `User`/`UserStatus` (уже есть)
- Produces: `UserService.getById(Integer): Mono<UserDto>`, `.getAll(): Flux<UserDto>`,
  `.create(String username): Mono<UserDto>`, `.rename(Integer id, String username): Mono<UserDto>`,
  `.delete(Integer id): Mono<UserDto>` (soft delete, возвращает обновлённый DTO со статусом BLOCKED)

Тестирование — юнит-тест с Mockito на замоканном `UserRepository` (не интеграционный: сервисная
логика — это оркестрация, реальная БД для неё не нужна, репозиторий уже покрыт интеграционными
тестами в предыдущем плане).

- [ ] **Step 1: Написать падающий тест**

```java
// src/test/java/org/example/filestorage/service/UserServiceTest.java
package org.example.filestorage.service;

import org.example.filestorage.dto.UserDto;
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
    void renameUpdatesUsername() {
        User existing = new User(1, "ivan", UserStatus.ACTIVE);
        User renamed = new User(1, "ivan2", UserStatus.ACTIVE);
        when(userRepository.findById(1)).thenReturn(Mono.just(existing));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(renamed));

        StepVerifier.create(userService.rename(1, "ivan2"))
                .expectNextMatches(dto -> dto.username().equals("ivan2"))
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.service.UserServiceTest"`
Expected: FAIL — компиляция упадёт (нет `UserService`, `UserServiceImpl`)

- [ ] **Step 3: Написать интерфейс сервиса**

```java
// src/main/java/org/example/filestorage/service/UserService.java
package org.example.filestorage.service;

import org.example.filestorage.dto.UserDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<UserDto> getById(Integer id);

    Flux<UserDto> getAll();

    Mono<UserDto> create(String username);

    Mono<UserDto> rename(Integer id, String username);

    Mono<UserDto> delete(Integer id);
}
```

- [ ] **Step 4: Написать реализацию**

```java
// src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java
package org.example.filestorage.service.impl;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.mapper.UserMapper;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.service.UserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public Mono<UserDto> getById(Integer id) {
        return userRepository.findById(id).map(userMapper::toDto);
    }

    @Override
    public Flux<UserDto> getAll() {
        return userRepository.findAll().map(userMapper::toDto);
    }

    @Override
    public Mono<UserDto> create(String username) {
        User user = new User(null, username, UserStatus.ACTIVE);
        return userRepository.save(user).map(userMapper::toDto);
    }

    @Override
    public Mono<UserDto> rename(Integer id, String username) {
        return userRepository.findById(id)
                .flatMap(user -> {
                    user.setUsername(username);
                    return userRepository.save(user);
                })
                .map(userMapper::toDto);
    }

    @Override
    public Mono<UserDto> delete(Integer id) {
        return userRepository.findById(id)
                .flatMap(user -> {
                    user.setStatus(UserStatus.BLOCKED);
                    return userRepository.save(user);
                })
                .map(userMapper::toDto);
    }
}
```

- [ ] **Step 5: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.service.UserServiceTest"`
Expected: PASS (3/3)

- [ ] **Step 6: Закоммитить**

```bash
git add src/main/java/org/example/filestorage/service/UserService.java \
        src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java \
        src/test/java/org/example/filestorage/service/UserServiceTest.java
git commit -m "Добавить UserService: CRUD, soft delete"
```

---

## Task 2: EventService — чтение, hard delete

**Files:**
- Create: `src/main/java/org/example/filestorage/service/EventService.java`
- Create: `src/main/java/org/example/filestorage/service/impl/EventServiceImpl.java`
- Test: `src/test/java/org/example/filestorage/service/EventServiceTest.java`

**Interfaces:**
- Consumes: `EventRepository` (уже есть, `ReactiveCrudRepository` даёт `deleteById` из коробки),
  `EventMapper` (уже есть, MapStruct)
- Produces: `EventService.getById(Integer): Mono<EventDto>`, `.getAll(): Flux<EventDto>`,
  `.delete(Integer id): Mono<Void>` (honest hard delete)

Важно: `EventService` **не имеет метода `create`** — событие создаётся исключительно как
побочный эффект `FileService.upload` (Task 4), самостоятельного API на создание Event нет и не
нужен по дизайн-документу.

- [ ] **Step 1: Написать падающий тест**

```java
// src/test/java/org/example/filestorage/service/EventServiceTest.java
package org.example.filestorage.service;

import org.example.filestorage.mapper.EventMapper;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.service.impl.EventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class EventServiceTest {

    private EventRepository eventRepository;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
        eventService = new EventServiceImpl(eventRepository, eventMapper);
    }

    @Test
    void getByIdReturnsMappedDto() {
        Event event = new Event(1, 10, 20, EventStatus.CREATED, LocalDateTime.now());
        when(eventRepository.findById(1)).thenReturn(Mono.just(event));

        StepVerifier.create(eventService.getById(1))
                .expectNextMatches(dto -> dto.id().equals(1) && dto.userId().equals(10) && dto.fileId().equals(20))
                .verifyComplete();
    }

    @Test
    void deleteRemovesRowFromRepository() {
        when(eventRepository.deleteById(1)).thenReturn(Mono.empty());

        StepVerifier.create(eventService.delete(1))
                .verifyComplete();

        verify(eventRepository).deleteById(1);
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.service.EventServiceTest"`
Expected: FAIL — компиляция упадёт (нет `EventService`, `EventServiceImpl`)

- [ ] **Step 3: Написать интерфейс сервиса**

```java
// src/main/java/org/example/filestorage/service/EventService.java
package org.example.filestorage.service;

import org.example.filestorage.dto.EventDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventService {

    Mono<EventDto> getById(Integer id);

    Flux<EventDto> getAll();

    Mono<Void> delete(Integer id);
}
```

- [ ] **Step 4: Написать реализацию**

```java
// src/main/java/org/example/filestorage/service/impl/EventServiceImpl.java
package org.example.filestorage.service.impl;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.mapper.EventMapper;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.service.EventService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public EventServiceImpl(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public Mono<EventDto> getById(Integer id) {
        return eventRepository.findById(id).map(eventMapper::toDto);
    }

    @Override
    public Flux<EventDto> getAll() {
        return eventRepository.findAll().map(eventMapper::toDto);
    }

    @Override
    public Mono<Void> delete(Integer id) {
        return eventRepository.deleteById(id);
    }
}
```

- [ ] **Step 5: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.service.EventServiceTest"`
Expected: PASS (2/2)

- [ ] **Step 6: Закоммитить**

```bash
git add src/main/java/org/example/filestorage/service/EventService.java \
        src/main/java/org/example/filestorage/service/impl/EventServiceImpl.java \
        src/test/java/org/example/filestorage/service/EventServiceTest.java
git commit -m "Добавить EventService: чтение, hard delete"
```

---

## Task 3: MinIO-инфраструктура + FileContentService

**Files:**
- Modify: `src/main/resources/application.yml` — добавить секцию `minio`
- Create: `src/main/java/org/example/filestorage/config/MinioProperties.java`
- Create: `src/main/java/org/example/filestorage/config/MinioConfig.java`
- Create: `src/main/java/org/example/filestorage/exception/FileStorageUnavailableException.java`
- Create: `src/main/java/org/example/filestorage/service/FileContentService.java`
- Create: `src/main/java/org/example/filestorage/service/impl/FileContentServiceImpl.java`
- Modify: `src/test/java/org/example/filestorage/AbstractIntegrationTest.java` — добавить
  singleton-контейнер MinIO + тестовый бакет + проброс конфига
- Test: `src/test/java/org/example/filestorage/service/FileContentServiceTest.java`

**Interfaces:**
- Produces: `MinioProperties(String endpoint, String accessKey, String secretKey, String bucket)`,
  `S3AsyncClient` bean, `FileStorageUnavailableException`,
  `FileContentService.put(String key, byte[] content): Mono<Void>`,
  `.get(String key): Mono<byte[]>`, `.delete(String key): Mono<Void>` — используется Task 4

**Важное техническое примечание для реализующего**: пакет и координаты Testcontainers-модуля
для MinIO на момент написания плана — `org.testcontainers:minio` (без явной версии, управляется
той же BOM от Spring Boot, что и уже используемые `testcontainers-mysql`/`testcontainers-r2dbc`),
класс `org.testcontainers.minio.MinIOContainer` (по аналогии с `org.testcontainers.mysql.MySQLContainer`,
который уже используется в проекте — Testcontainers 2.x кладёт классы модулей в
`org.testcontainers.<module>`, не в общий `org.testcontainers.containers`). Если импорт не
скомпилируется — это первое, что нужно перепроверить в Maven Central/актуальной документации
модуля, версия/пакет могли измениться с момента написания этого плана.

- [ ] **Step 1: Добавить зависимость на MinIO-модуль Testcontainers**

В `build.gradle`, в блок `dependencies { ... }`, рядом с уже существующими `testcontainers-*`
записями:

```groovy
testImplementation 'org.testcontainers:minio'
```

- [ ] **Step 2: Написать падающий тест**

```java
// src/test/java/org/example/filestorage/service/FileContentServiceTest.java
package org.example.filestorage.service;

import org.example.filestorage.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

class FileContentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FileContentService fileContentService;

    @Test
    void putsAndGetsContentByKey() {
        String key = "test-key-put-get";
        byte[] content = "hello minio".getBytes();

        fileContentService.put(key, content).block();

        StepVerifier.create(fileContentService.get(key))
                .expectNextMatches(bytes -> new String(bytes).equals("hello minio"))
                .verifyComplete();
    }

    @Test
    void deletesContent() {
        String key = "test-key-delete";
        fileContentService.put(key, "to be deleted".getBytes()).block();

        fileContentService.delete(key).block();

        // Не важно, что именно за исключение (NoSuchKey — "не найдено", а не "сервис лёг") —
        // сейчас FileContentService заворачивает любую ошибку get() одинаково, это упрощение,
        // см. Step 7: различать not-found и unavailable вне рамок дизайн-документа
        StepVerifier.create(fileContentService.get(key))
                .expectError()
                .verify();
    }
}
```

- [ ] **Step 3: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.service.FileContentServiceTest"`
Expected: FAIL — компиляция упадёт (нет `FileContentService`, `FileStorageUnavailableException`)

- [ ] **Step 4: Добавить секцию MinIO в application.yml**

```yaml
# src/main/resources/application.yml — добавить в конец файла, вместо TODO-комментария
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:files}
```

- [ ] **Step 5: Написать MinioProperties и MinioConfig**

```java
// src/main/java/org/example/filestorage/config/MinioProperties.java
package org.example.filestorage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
```

```java
// src/main/java/org/example/filestorage/config/MinioConfig.java
package org.example.filestorage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public S3AsyncClient s3AsyncClient(MinioProperties properties) {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true) // MinIO не поддерживает virtual-hosted addressing как настоящий AWS S3
                .build();
    }
}
```

- [ ] **Step 6: Написать FileStorageUnavailableException**

```java
// src/main/java/org/example/filestorage/exception/FileStorageUnavailableException.java
package org.example.filestorage.exception;

public class FileStorageUnavailableException extends RuntimeException {

    public FileStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 7: Написать FileContentService**

```java
// src/main/java/org/example/filestorage/service/FileContentService.java
package org.example.filestorage.service;

import reactor.core.publisher.Mono;

public interface FileContentService {

    Mono<Void> put(String key, byte[] content);

    Mono<byte[]> get(String key);

    Mono<Void> delete(String key);
}
```

```java
// src/main/java/org/example/filestorage/service/impl/FileContentServiceImpl.java
package org.example.filestorage.service.impl;

import org.example.filestorage.config.MinioProperties;
import org.example.filestorage.exception.FileStorageUnavailableException;
import org.example.filestorage.service.FileContentService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class FileContentServiceImpl implements FileContentService {

    private final S3AsyncClient s3AsyncClient;
    private final MinioProperties minioProperties;

    public FileContentServiceImpl(S3AsyncClient s3AsyncClient, MinioProperties minioProperties) {
        this.s3AsyncClient = s3AsyncClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public Mono<Void> put(String key, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        return Mono.fromFuture(() -> s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(content)))
                .then()
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось сохранить файл в MinIO: " + key, e));
    }

    @Override
    public Mono<byte[]> get(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        // упрощение: не различаем "объекта нет" (NoSuchKeyException) от "MinIO реально недоступен" —
        // оба заворачиваются одинаково; различение этих двух случаев — вне рамок дизайн-документа
        return Mono.fromFuture(() -> s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(bytes -> bytes.asByteArray())
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось получить файл из MinIO: " + key, e));
    }

    @Override
    public Mono<Void> delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        return Mono.fromFuture(() -> s3AsyncClient.deleteObject(request))
                .then()
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось удалить файл из MinIO: " + key, e));
    }
}
```

- [ ] **Step 8: Дополнить AbstractIntegrationTest контейнером MinIO и тестовым бакетом**

Заменить содержимое файла целиком на:

```java
// src/test/java/org/example/filestorage/AbstractIntegrationTest.java
package org.example.filestorage;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.minio.MinIOContainer;
import org.testcontainers.mysql.MySQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final String TEST_BUCKET = "test-bucket";

    static final MySQLContainer MYSQL;
    static final MinIOContainer MINIO;

    static {
        MYSQL = new MySQLContainer("mysql:8.4");
        MYSQL.start();

        MINIO = new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z");
        MINIO.start();

        // в проде бакет создаёт docker-compose заранее (см. дизайн-документ — приложение
        // не создаёт бакет само); здесь эту роль на себя берёт тестовая инфраструктура
        S3AsyncClient setupClient = S3AsyncClient.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .forcePathStyle(true)
                .build();
        setupClient.createBucket(b -> b.bucket(TEST_BUCKET)).join();
        setupClient.close();
    }

    @Autowired
    private DatabaseClient databaseClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String r2dbcUrl = String.format("r2dbc:mysql://%s:%d/%s",
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName());

        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", MYSQL::getUsername);
        registry.add("spring.r2dbc.password", MYSQL::getPassword);

        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);

        registry.add("minio.endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
        registry.add("minio.bucket", () -> TEST_BUCKET);
    }

    // Shared container + shared Spring context across the whole suite means rows pile up
    // across test classes. Wipe child-to-parent (FK order) after every test so each test
    // starts from a clean table, not just a clean row.
    @AfterEach
    void cleanDatabase() {
        databaseClient.sql("DELETE FROM events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM files").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM users").fetch().rowsUpdated().block();
    }
}
```

- [ ] **Step 9: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.service.FileContentServiceTest"`
Expected: PASS (2/2). Если импорт `org.testcontainers.minio.MinIOContainer` не находится —
проверить актуальный пакет/координаты модуля (см. примечание выше) и поправить импорт.

- [ ] **Step 10: Прогнать весь набор тестов, убедиться что старые не сломались**

Run: `./gradlew test`
Expected: PASS — все тесты, включая старые (User/File/Event repository, мапперы), зелёные

- [ ] **Step 11: Закоммитить**

```bash
git add build.gradle src/main/resources/application.yml \
        src/main/java/org/example/filestorage/config \
        src/main/java/org/example/filestorage/exception \
        src/main/java/org/example/filestorage/service/FileContentService.java \
        src/main/java/org/example/filestorage/service/impl/FileContentServiceImpl.java \
        src/test/java/org/example/filestorage/AbstractIntegrationTest.java \
        src/test/java/org/example/filestorage/service/FileContentServiceTest.java
git commit -m "Добавить MinIO-инфраструктуру и FileContentService"
```

---

## Task 4: FileService — CRUD, upload-flow, soft delete

**Files:**
- Create: `src/main/java/org/example/filestorage/service/FileService.java`
- Create: `src/main/java/org/example/filestorage/service/impl/FileServiceImpl.java`
- Test: `src/test/java/org/example/filestorage/service/FileServiceTest.java`

**Interfaces:**
- Consumes: `FileRepository`, `EventRepository`, `FileMapper` (все уже есть),
  `FileContentService` (Task 3) — `put(String, byte[]): Mono<Void>`, `get(String): Mono<byte[]>`
- Produces: `FileService.getById(Integer): Mono<FileDto>`, `.getAll(): Flux<FileDto>`,
  `.upload(String name, byte[] content, Integer userId): Mono<FileDto>`,
  `.getContent(Integer id): Mono<byte[]>`, `.delete(Integer id): Mono<FileDto>` (soft delete)

Это единственный тест в этом плане, требующий `AbstractIntegrationTest` (реальные MySQL+MinIO) —
`upload` оркестрирует ОБЕ системы разом, и именно эта оркестрация — то, что нужно проверить не
на моках.

- [ ] **Step 1: Написать падающий тест**

```java
// src/test/java/org/example/filestorage/service/FileServiceTest.java
package org.example.filestorage.service;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.FileDto;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.repository.FileRepository;
import org.example.filestorage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void uploadStoresContentInMinioAndCreatesFilePlusEvent() {
        User user = userRepository.save(new User(null, "ivan", UserStatus.ACTIVE)).block();
        byte[] content = "hello world".getBytes();

        FileDto dto = fileService.upload("report.pdf", content, user.getId()).block();

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("report.pdf");
        assertThat(dto.status()).isEqualTo(FileStatus.ACTIVE);

        // содержимое реально долетело до MinIO, а не только записалось в БД
        StepVerifier.create(fileService.getContent(dto.id()))
                .expectNextMatches(bytes -> new String(bytes).equals("hello world"))
                .verifyComplete();

        // Event реально создался с правильным владельцем (через существующий owner-lookup запрос)
        StepVerifier.create(eventRepository.findOwnerUserIdByFileId(dto.id()))
                .expectNext(user.getId())
                .verifyComplete();
    }

    @Test
    void deleteArchivesFileWithoutRemovingRow() {
        User user = userRepository.save(new User(null, "petr", UserStatus.ACTIVE)).block();
        FileDto uploaded = fileService.upload("doc.pdf", "content".getBytes(), user.getId()).block();

        FileDto deleted = fileService.delete(uploaded.id()).block();

        assertThat(deleted.status()).isEqualTo(FileStatus.ARCHIVED);
        StepVerifier.create(fileRepository.findById(uploaded.id()))
                .expectNextMatches(f -> f.getStatus() == FileStatus.ARCHIVED)
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.service.FileServiceTest"`
Expected: FAIL — компиляция упадёт (нет `FileService`, `FileServiceImpl`)

- [ ] **Step 3: Написать интерфейс сервиса**

```java
// src/main/java/org/example/filestorage/service/FileService.java
package org.example.filestorage.service;

import org.example.filestorage.dto.FileDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileService {

    Mono<FileDto> getById(Integer id);

    Flux<FileDto> getAll();

    Mono<FileDto> upload(String name, byte[] content, Integer userId);

    Mono<byte[]> getContent(Integer id);

    Mono<FileDto> delete(Integer id);
}
```

- [ ] **Step 4: Написать реализацию**

```java
// src/main/java/org/example/filestorage/service/impl/FileServiceImpl.java
package org.example.filestorage.service.impl;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.mapper.FileMapper;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.repository.FileRepository;
import org.example.filestorage.service.FileContentService;
import org.example.filestorage.service.FileService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final EventRepository eventRepository;
    private final FileContentService fileContentService;
    private final FileMapper fileMapper;

    public FileServiceImpl(FileRepository fileRepository,
                            EventRepository eventRepository,
                            FileContentService fileContentService,
                            FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.eventRepository = eventRepository;
        this.fileContentService = fileContentService;
        this.fileMapper = fileMapper;
    }

    @Override
    public Mono<FileDto> getById(Integer id) {
        return fileRepository.findById(id).map(fileMapper::toDto);
    }

    @Override
    public Flux<FileDto> getAll() {
        return fileRepository.findAll().map(fileMapper::toDto);
    }

    @Override
    public Mono<FileDto> upload(String name, byte[] content, Integer userId) {
        String key = UUID.randomUUID().toString();

        return fileContentService.put(key, content)
                .then(fileRepository.save(new File(null, name, key, FileStatus.ACTIVE)))
                .flatMap(savedFile -> {
                    Event event = new Event(null, userId, savedFile.getId(), EventStatus.CREATED,
                            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    return eventRepository.save(event).thenReturn(savedFile);
                })
                .map(fileMapper::toDto);
    }

    @Override
    public Mono<byte[]> getContent(Integer id) {
        return fileRepository.findById(id)
                .flatMap(file -> fileContentService.get(file.getLocation()));
    }

    @Override
    public Mono<FileDto> delete(Integer id) {
        return fileRepository.findById(id)
                .flatMap(file -> {
                    file.setStatus(FileStatus.ARCHIVED);
                    return fileRepository.save(file);
                })
                .map(fileMapper::toDto);
    }
}
```

- [ ] **Step 5: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.service.FileServiceTest"`
Expected: PASS (2/2)

- [ ] **Step 6: Прогнать весь набор тестов**

Run: `./gradlew test`
Expected: PASS — все тесты проекта зелёные (репозитории, мапперы, UserService, EventService,
FileContentService, FileService)

- [ ] **Step 7: Закоммитить**

```bash
git add src/main/java/org/example/filestorage/service/FileService.java \
        src/main/java/org/example/filestorage/service/impl/FileServiceImpl.java \
        src/test/java/org/example/filestorage/service/FileServiceTest.java
git commit -m "Добавить FileService: upload-flow, CRUD, soft delete"
```
