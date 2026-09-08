# Разметка модели данных Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать модель данных практики (User/File/Event) — миграции, entity, репозитории, DTO и мапперы — по дизайн-документу `docs/superpowers/specs/2026-09-07-data-model-design.md`.

**Architecture:** Реактивный стек: Spring Data R2DBC (не JPA), MySQL через `io.asyncer:r2dbc-mysql`, Flyway через отдельный blocking JDBC-драйвер. Интеграционные тесты — Testcontainers (единый MySQL-контейнер на тест-класс, с ручным пробросом и R2DBC-, и JDBC-подключения на него же).

**Tech Stack:** Spring Boot 4.1.1, Spring WebFlux, Spring Data R2DBC, Flyway, MySQL 8, Testcontainers 2.x, JUnit 5, Reactor Test, Lombok, Java 25.

## Global Constraints

- Сущности и их поля — строго по DDL ментора (`PROGRESS.md` → «Задача от ментора»), никаких полей сверх минимума
- Repository — только `ReactiveCrudRepository`/производные из Spring Data R2DBC, никакого `JpaRepository`/блокирующих вызовов
- Один DTO-класс на сущность (не по ролям)
- `location` (`File`) никогда не попадает в DTO — см. дизайн-документ, раздел «Доступ к содержимому файла»
- FK-constraints — на дефолтном движке хранения MySQL, `ENGINE=` не указывается явно
- Владелец файла — через `events.status = 'CREATED'`, прямой связи `files → users` нет
- Базовый пакет — `org.example.filestorage`; слои — `model`/`repository`/`dto`/`mapper` (плоско, без вложенности)
- Вне рамок этого плана: JWT/Security, REST-контроллеры, upload-flow сервис (MinIO), тестовая стратегия сверх интеграционных тестов репозиториев — по дизайн-документу это отдельные, ещё не обсуждённые шаги

---

## Task 1: Users — миграция, entity, репозиторий + тестовая инфраструктура

**Files:**
- Create: `src/main/resources/db/migration/V1__create_users.sql`
- Create: `src/main/java/org/example/filestorage/model/UserStatus.java`
- Create: `src/main/java/org/example/filestorage/model/User.java`
- Create: `src/main/java/org/example/filestorage/repository/UserRepository.java`
- Create: `src/test/java/org/example/filestorage/AbstractIntegrationTest.java`
- Test: `src/test/java/org/example/filestorage/repository/UserRepositoryTest.java`

**Interfaces:**
- Produces: `AbstractIntegrationTest` (абстрактный базовый класс, `@SpringBootTest` + `@Testcontainers`, единый `MySQLContainer`, прописывает `spring.r2dbc.*` и `spring.flyway.*` на этот же контейнер) — используется задачами 2 и 3
- Produces: `User(Integer id, String username, UserStatus status)`, `UserRepository extends ReactiveCrudRepository<User, Integer>`

- [ ] **Step 1: Написать тестовую инфраструктуру**

```java
// src/test/java/org/example/filestorage/AbstractIntegrationTest.java
package org.example.filestorage;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

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
    }
}
```

Это не сам тест (нет `@Test`-методов), а общая база — тут ничего "падать" не будет, но следующий шаг уже проверит, что она реально поднимает контейнер.

- [ ] **Step 2: Написать падающий тест для User**

```java
// src/test/java/org/example/filestorage/repository/UserRepositoryTest.java
package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.User;
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
        User user = new User(null, "ivan", UserStatus.ACTIVE);

        User saved = userRepository.save(user).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        StepVerifier.create(userRepository.findById(saved.getId()))
                .expectNextMatches(found ->
                        found.getUsername().equals("ivan") && found.getStatus() == UserStatus.ACTIVE)
                .verifyComplete();
    }
}
```

- [ ] **Step 3: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.repository.UserRepositoryTest"`
Expected: FAIL — компиляция упадёт (нет `User`, `UserStatus`, `UserRepository`, таблицы `users`)

- [ ] **Step 4: Написать миграцию**

```sql
-- src/main/resources/db/migration/V1__create_users.sql
CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL
);
```

- [ ] **Step 5: Написать enum и entity**

```java
// src/main/java/org/example/filestorage/model/UserStatus.java
package org.example.filestorage.model;

public enum UserStatus {
    ACTIVE,
    BLOCKED
}
```

```java
// src/main/java/org/example/filestorage/model/User.java
package org.example.filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @Id
    private Integer id;
    private String username;
    private UserStatus status;
}
```

- [ ] **Step 6: Написать репозиторий**

```java
// src/main/java/org/example/filestorage/repository/UserRepository.java
package org.example.filestorage.repository;

import org.example.filestorage.model.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRepository extends ReactiveCrudRepository<User, Integer> {
}
```

- [ ] **Step 7: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.repository.UserRepositoryTest"`
Expected: PASS

- [ ] **Step 8: Закоммитить**

```bash
git add src/main/resources/db/migration/V1__create_users.sql \
        src/main/java/org/example/filestorage/model/User.java \
        src/main/java/org/example/filestorage/model/UserStatus.java \
        src/main/java/org/example/filestorage/repository/UserRepository.java \
        src/test/java/org/example/filestorage/AbstractIntegrationTest.java \
        src/test/java/org/example/filestorage/repository/UserRepositoryTest.java
git commit -m "Добавить сущность User: миграция, entity, репозиторий"
```

---

## Task 2: Files — миграция, entity, репозиторий

**Files:**
- Create: `src/main/resources/db/migration/V2__create_files.sql`
- Create: `src/main/java/org/example/filestorage/model/FileStatus.java`
- Create: `src/main/java/org/example/filestorage/model/File.java`
- Create: `src/main/java/org/example/filestorage/repository/FileRepository.java`
- Test: `src/test/java/org/example/filestorage/repository/FileRepositoryTest.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest` (Task 1)
- Produces: `File(Integer id, String name, String location, FileStatus status)`, `FileRepository extends ReactiveCrudRepository<File, Integer>`

- [ ] **Step 1: Написать падающий тест**

```java
// src/test/java/org/example/filestorage/repository/FileRepositoryTest.java
package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FileRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private FileRepository fileRepository;

    @Test
    void savesAndFindsFileById() {
        File file = new File(null, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE);

        File saved = fileRepository.save(file).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        StepVerifier.create(fileRepository.findById(saved.getId()))
                .expectNextMatches(found ->
                        found.getName().equals("report.pdf") && found.getStatus() == FileStatus.ACTIVE)
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.repository.FileRepositoryTest"`
Expected: FAIL — компиляция упадёт (нет `File`, `FileStatus`, `FileRepository`, таблицы `files`)

- [ ] **Step 3: Написать миграцию**

```sql
-- src/main/resources/db/migration/V2__create_files.sql
CREATE TABLE files (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  location VARCHAR(500) NOT NULL,
  status VARCHAR(50) NOT NULL
);
```

- [ ] **Step 4: Написать enum и entity**

```java
// src/main/java/org/example/filestorage/model/FileStatus.java
package org.example.filestorage.model;

public enum FileStatus {
    ACTIVE,
    ARCHIVED
}
```

```java
// src/main/java/org/example/filestorage/model/File.java
package org.example.filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("files")
public class File {

    @Id
    private Integer id;
    private String name;
    private String location;
    private FileStatus status;
}
```

- [ ] **Step 5: Написать репозиторий**

```java
// src/main/java/org/example/filestorage/repository/FileRepository.java
package org.example.filestorage.repository;

import org.example.filestorage.model.File;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FileRepository extends ReactiveCrudRepository<File, Integer> {
}
```

- [ ] **Step 6: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.repository.FileRepositoryTest"`
Expected: PASS

- [ ] **Step 7: Закоммитить**

```bash
git add src/main/resources/db/migration/V2__create_files.sql \
        src/main/java/org/example/filestorage/model/File.java \
        src/main/java/org/example/filestorage/model/FileStatus.java \
        src/main/java/org/example/filestorage/repository/FileRepository.java \
        src/test/java/org/example/filestorage/repository/FileRepositoryTest.java
git commit -m "Добавить сущность File: миграция, entity, репозиторий"
```

---

## Task 3: Events — миграция с FK, entity, репозиторий с поиском владельца

**Files:**
- Create: `src/main/resources/db/migration/V3__create_events.sql`
- Create: `src/main/java/org/example/filestorage/model/EventStatus.java`
- Create: `src/main/java/org/example/filestorage/model/Event.java`
- Create: `src/main/java/org/example/filestorage/repository/EventRepository.java`
- Test: `src/test/java/org/example/filestorage/repository/EventRepositoryTest.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest` (Task 1), `UserRepository`/`User` (Task 1), `FileRepository`/`File` (Task 2)
- Produces: `Event(Integer id, Integer userId, Integer fileId, EventStatus status, LocalDateTime timestamp)`, `EventRepository.findOwnerUserIdByFileId(Integer fileId): Mono<Integer>`

- [ ] **Step 1: Написать падающие тесты**

```java
// src/test/java/org/example/filestorage/repository/EventRepositoryTest.java
package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

class EventRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void savesEventLinkedToRealUserAndFile() {
        User user = userRepository.save(new User(null, "ivan", UserStatus.ACTIVE)).block();
        File file = fileRepository.save(new File(null, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE)).block();

        Event event = new Event(null, user.getId(), file.getId(), EventStatus.CREATED, LocalDateTime.now());

        StepVerifier.create(eventRepository.save(event))
                .expectNextMatches(saved -> saved.getId() != null && saved.getStatus() == EventStatus.CREATED)
                .verifyComplete();
    }

    @Test
    void rejectsEventWithNonExistentUser() {
        Event event = new Event(null, 999_999, null, EventStatus.CREATED, LocalDateTime.now());

        StepVerifier.create(eventRepository.save(event))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void findsOwnerByCreatedEvent() {
        User user = userRepository.save(new User(null, "petr", UserStatus.ACTIVE)).block();
        File file = fileRepository.save(new File(null, "doc.pdf", "bucket/doc.pdf", FileStatus.ACTIVE)).block();
        eventRepository.save(new Event(null, user.getId(), file.getId(), EventStatus.CREATED, LocalDateTime.now())).block();

        StepVerifier.create(eventRepository.findOwnerUserIdByFileId(file.getId()))
                .expectNext(user.getId())
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `./gradlew test --tests "org.example.filestorage.repository.EventRepositoryTest"`
Expected: FAIL — компиляция упадёт (нет `Event`, `EventStatus`, `EventRepository`, таблицы `events`)

- [ ] **Step 3: Написать миграцию**

```sql
-- src/main/resources/db/migration/V3__create_events.sql
CREATE TABLE events (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT,
  file_id INT,
  status VARCHAR(50) NOT NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (file_id) REFERENCES files(id)
);
```

- [ ] **Step 4: Написать enum и entity**

```java
// src/main/java/org/example/filestorage/model/EventStatus.java
package org.example.filestorage.model;

public enum EventStatus {
    CREATED,
    UPDATED,
    DELETED
}
```

```java
// src/main/java/org/example/filestorage/model/Event.java
package org.example.filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("events")
public class Event {

    @Id
    private Integer id;
    private Integer userId;
    private Integer fileId;
    private EventStatus status;
    private LocalDateTime timestamp;
}
```

- [ ] **Step 5: Написать репозиторий с запросом владельца**

```java
// src/main/java/org/example/filestorage/repository/EventRepository.java
package org.example.filestorage.repository;

import org.example.filestorage.model.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface EventRepository extends ReactiveCrudRepository<Event, Integer> {

    @Query("SELECT user_id FROM events WHERE file_id = :fileId AND status = 'CREATED'")
    Mono<Integer> findOwnerUserIdByFileId(Integer fileId);
}
```

- [ ] **Step 6: Запустить тесты, убедиться что проходят**

Run: `./gradlew test --tests "org.example.filestorage.repository.EventRepositoryTest"`
Expected: PASS (все три теста)

- [ ] **Step 7: Закоммитить**

```bash
git add src/main/resources/db/migration/V3__create_events.sql \
        src/main/java/org/example/filestorage/model/Event.java \
        src/main/java/org/example/filestorage/model/EventStatus.java \
        src/main/java/org/example/filestorage/repository/EventRepository.java \
        src/test/java/org/example/filestorage/repository/EventRepositoryTest.java
git commit -m "Добавить сущность Event: миграция с FK, entity, поиск владельца файла"
```

---

## Task 4: DTO и мапперы для всех трёх сущностей

**Files:**
- Create: `src/main/java/org/example/filestorage/dto/UserDto.java`
- Create: `src/main/java/org/example/filestorage/dto/FileDto.java`
- Create: `src/main/java/org/example/filestorage/dto/EventDto.java`
- Create: `src/main/java/org/example/filestorage/mapper/UserMapper.java`
- Create: `src/main/java/org/example/filestorage/mapper/FileMapper.java`
- Create: `src/main/java/org/example/filestorage/mapper/EventMapper.java`
- Test: `src/test/java/org/example/filestorage/mapper/UserMapperTest.java`
- Test: `src/test/java/org/example/filestorage/mapper/FileMapperTest.java`
- Test: `src/test/java/org/example/filestorage/mapper/EventMapperTest.java`

**Interfaces:**
- Consumes: `User`/`File`/`Event` entities (Tasks 1-3)
- Produces: `UserDto(Integer id, String username, UserStatus status)`, `FileDto(Integer id, String name, FileStatus status)` (без `location`!), `EventDto(Integer id, Integer userId, Integer fileId, EventStatus status, LocalDateTime timestamp)`, мапперы `toDto(Entity): Dto` для каждого

Эти тесты — чистые unit-тесты (без `AbstractIntegrationTest`, без БД) — маппер просто переносит поля.

- [ ] **Step 1: Написать падающий тест для UserMapper**

```java
// src/test/java/org/example/filestorage/mapper/UserMapperTest.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void mapsUserToDto() {
        User user = new User(1, "ivan", UserStatus.ACTIVE);

        UserDto dto = mapper.toDto(user);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.username()).isEqualTo("ivan");
        assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.mapper.UserMapperTest"`
Expected: FAIL — компиляция упадёт (нет `UserDto`, `UserMapper`)

- [ ] **Step 3: Написать UserDto и UserMapper**

```java
// src/main/java/org/example/filestorage/dto/UserDto.java
package org.example.filestorage.dto;

import org.example.filestorage.model.UserStatus;

public record UserDto(Integer id, String username, UserStatus status) {
}
```

```java
// src/main/java/org/example/filestorage/mapper/UserMapper.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getStatus());
    }
}
```

- [ ] **Step 4: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.mapper.UserMapperTest"`
Expected: PASS

- [ ] **Step 5: Написать падающий тест для FileMapper**

```java
// src/test/java/org/example/filestorage/mapper/FileMapperTest.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileMapperTest {

    private final FileMapper mapper = new FileMapper();

    @Test
    void mapsFileToDtoWithoutLocation() {
        File file = new File(1, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE);

        FileDto dto = mapper.toDto(file);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.name()).isEqualTo("report.pdf");
        assertThat(dto.status()).isEqualTo(FileStatus.ACTIVE);
    }
}
```

- [ ] **Step 6: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.mapper.FileMapperTest"`
Expected: FAIL — компиляция упадёт (нет `FileDto`, `FileMapper`)

- [ ] **Step 7: Написать FileDto и FileMapper**

```java
// src/main/java/org/example/filestorage/dto/FileDto.java
package org.example.filestorage.dto;

import org.example.filestorage.model.FileStatus;

// без location — см. дизайн-документ, раздел "Доступ к содержимому файла"
public record FileDto(Integer id, String name, FileStatus status) {
}
```

```java
// src/main/java/org/example/filestorage/mapper/FileMapper.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.model.File;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {

    public FileDto toDto(File file) {
        return new FileDto(file.getId(), file.getName(), file.getStatus());
    }
}
```

- [ ] **Step 8: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.mapper.FileMapperTest"`
Expected: PASS

- [ ] **Step 9: Написать падающий тест для EventMapper**

```java
// src/test/java/org/example/filestorage/mapper/EventMapperTest.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper();

    @Test
    void mapsEventToDto() {
        LocalDateTime now = LocalDateTime.now();
        Event event = new Event(1, 10, 20, EventStatus.CREATED, now);

        EventDto dto = mapper.toDto(event);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.userId()).isEqualTo(10);
        assertThat(dto.fileId()).isEqualTo(20);
        assertThat(dto.status()).isEqualTo(EventStatus.CREATED);
        assertThat(dto.timestamp()).isEqualTo(now);
    }
}
```

- [ ] **Step 10: Запустить тест, убедиться что падает**

Run: `./gradlew test --tests "org.example.filestorage.mapper.EventMapperTest"`
Expected: FAIL — компиляция упадёт (нет `EventDto`, `EventMapper`)

- [ ] **Step 11: Написать EventDto и EventMapper**

```java
// src/main/java/org/example/filestorage/dto/EventDto.java
package org.example.filestorage.dto;

import org.example.filestorage.model.EventStatus;

import java.time.LocalDateTime;

public record EventDto(Integer id, Integer userId, Integer fileId, EventStatus status, LocalDateTime timestamp) {
}
```

```java
// src/main/java/org/example/filestorage/mapper/EventMapper.java
package org.example.filestorage.mapper;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.model.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventDto toDto(Event event) {
        return new EventDto(event.getId(), event.getUserId(), event.getFileId(), event.getStatus(), event.getTimestamp());
    }
}
```

- [ ] **Step 12: Запустить тест, убедиться что проходит**

Run: `./gradlew test --tests "org.example.filestorage.mapper.EventMapperTest"`
Expected: PASS

- [ ] **Step 13: Прогнать весь набор тестов разом**

Run: `./gradlew test`
Expected: PASS — все тесты (репозитории + мапперы) зелёные

- [ ] **Step 14: Закоммитить**

```bash
git add src/main/java/org/example/filestorage/dto/ \
        src/main/java/org/example/filestorage/mapper/ \
        src/test/java/org/example/filestorage/mapper/
git commit -m "Добавить DTO и мапперы для User/File/Event"
```
