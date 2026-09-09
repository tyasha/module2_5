# HTTP-контроллеры и JWT-аутентификация Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Поднять HTTP-слой (`UserController`/`FileController`/`EventController`/`AuthController`)
поверх уже готового сервисного слоя, с JWT-аутентификацией (регистрация/логин, проверка токена на
защищённых путях).

**Architecture:** Аннотационные `@RestController` (не функциональные `RouterFunction`), методы
возвращают `Mono`/`Flux`. Реактивная Security-цепочка (`SecurityWebFilterChain`) с кастомным
JWT-фильтром: `/auth/**` открыт, всё остальное требует валидный `Bearer`-токен. Единый
`@RestControllerAdvice` маппит исключения сервисного слоя в HTTP-коды.

**Tech Stack:** Spring WebFlux, Spring Security (reactive), `jjwt` 0.13.0, `jakarta.validation`,
MySQL/R2DBC (уже настроено), Testcontainers + `WebTestClient` для интеграционных тестов.

## Global Constraints

- Реактивный стек целиком — ни одного блокирующего вызова (`.block()`) в `src/main` (проверять
  grep'ом после каждой задачи, как это уже делалось в предыдущих планах этого проекта)
- `jjwt` 0.13.0 — **современный fluent API**, проверено по реальным исходникам библиотеки:
  `Jwts.builder()...claim(...).signWith(key).compact()` для генерации,
  `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` для разбора (возвращает
  `Jws<Claims>` напрямую, без каста). **Не** использовать устаревший `parserBuilder()`/`parse()`
  стиль
- Валидация входных DTO — через `jakarta.validation`-аннотации + `@Valid`, не ручные проверки
- Формат ошибок — единый JSON `{"error": "...", "status": N}` через `ErrorResponse`-record,
  собирается в `@RestControllerAdvice`
- **Ролевая авторизация — вне рамок этого плана.** Любой аутентифицированный (с валидным токеном)
  юзер может дёргать любую защищённую ручку, независимо от роли. Проверяется только "есть ли
  валидный токен", не "какая у него роль". Роль лежит в токене и в БД, но нигде не используется
  для ограничения доступа — это отдельный следующий план
- `DataIntegrityViolationException` → HTTP `409`
- **Versioning через `Accept`-заголовок (решение из дизайн-документа) в этом плане не реализуется
  технически** — ни в одной задаче нет `produces = "application/vnd...`. Осознанно: версия ровно
  одна, различать пока нечего — строить механизм content negotiation под единственную версию было
  бы кодом без функции (YAGNI). Когда появится вторая версия — тогда и добавляется `produces` на
  конкретные методы. Пути сейчас без версии в URI (`/users`, `/files`, `/events`, `/auth`) — это и
  есть вся видимая часть решения на данный момент
- Пакеты: `org.example.filestorage.{model,repository,dto,mapper,service,service.impl,config,exception,security,controller}`
- Стиль кода — как в остальном проекте: `@Slf4j` + `doOnSuccess`/`doOnError` (с фильтрацией
  ожидаемых исключений типа `NotFoundException`/`BadCredentialsException` из `ERROR`-уровня),
  конструкторная инъекция без `@Autowired` на конструкторе, комментарии только на неочевидное

---

### Task 1: User — поля password/role, миграция, UserRole enum

**Files:**
- Create: `src/main/resources/db/migration/V4__add_auth_fields_to_users.sql`
- Create: `src/main/java/org/example/filestorage/model/UserRole.java`
- Modify: `src/main/java/org/example/filestorage/model/User.java`
- Modify: `src/main/java/org/example/filestorage/dto/UserDto.java`
- Modify: `src/main/java/org/example/filestorage/repository/UserRepository.java`
- Modify: `src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java` (только чтобы
  сохранить компиляцию — реальный сигнатурный рефакторинг `create()` в Task 3)
- Modify (обновить конструктор `new User(...)` под новую сигнатуру, 5 аргументов вместо 3):
  `src/test/java/org/example/filestorage/repository/EventRepositoryTest.java`
  `src/test/java/org/example/filestorage/repository/UserRepositoryTest.java`
  `src/test/java/org/example/filestorage/service/UserServiceTest.java`
  `src/test/java/org/example/filestorage/service/FileServiceTest.java`
- Test: `src/test/java/org/example/filestorage/repository/UserRepositoryTest.java` (новый тест на
  `findByUsername`)

**Interfaces:**
- Produces: `UserRole` enum (`ADMIN`, `MODERATOR`, `USER`); `User` — новые поля
  `password` (String), `role` (UserRole), новый порядок конструктора
  `User(Integer id, String username, String password, UserRole role, UserStatus status)`;
  `UserDto` — новое поле `role` (без `password`); `UserRepository.findByUsername(String username): Mono<User>`

- [ ] **Step 1: Миграция**

```sql
-- V4__add_auth_fields_to_users.sql
ALTER TABLE users
    ADD COLUMN password VARCHAR(255) NOT NULL,
    ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'USER';
```

- [ ] **Step 2: `UserRole` enum**

```java
package org.example.filestorage.model;

public enum UserRole {
    ADMIN,
    MODERATOR,
    USER
}
```

- [ ] **Step 3: Обновить `User.java`**

```java
package org.example.filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @Id
    @Column("id")
    private Integer id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("role")
    private UserRole role;

    @Column("status")
    private UserStatus status;
}
```

- [ ] **Step 4: Обновить `UserDto.java`**

```java
package org.example.filestorage.dto;

import org.example.filestorage.model.UserRole;
import org.example.filestorage.model.UserStatus;

// password осознанно отсутствует — не поле для маппинга, а не забытое (как и location у FileDto)
public record UserDto(Integer id, String username, UserRole role, UserStatus status) {
}
```

- [ ] **Step 5: `findByUsername` в `UserRepository.java`**

```java
package org.example.filestorage.repository;

import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Integer> {

    Mono<User> findByIdAndStatus(Integer id, UserStatus status);

    Flux<User> findAllByStatus(UserStatus status);

    Mono<User> findByUsername(String username);
}
```

- [ ] **Step 6: Временно поправить `UserServiceImpl.create()`, чтобы проект компилировался**
  (это временный минимум — реальный сигнатурный рефакторинг под пароль будет в Task 3)

В `UserServiceImpl.java` заменить строку `User user = new User(null, username, UserStatus.ACTIVE);`
на:

```java
        User user = new User(null, username, "", UserRole.USER, UserStatus.ACTIVE);
```

(добавить `import org.example.filestorage.model.UserRole;` в начало файла)

- [ ] **Step 7: Обновить конструкторы `new User(...)` в тестах под новую сигнатуру**

В `EventRepositoryTest.java` (строки 33, 62) заменить `new User(null, "ivan", UserStatus.ACTIVE)` на:
```java
new User(null, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Строка 73 — `new User(null, "petr", UserStatus.ACTIVE)` →
```java
new User(null, "petr", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Строка 74 — `new User(null, "sidor", UserStatus.ACTIVE)` →
```java
new User(null, "sidor", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Добавить `import org.example.filestorage.model.UserRole;` в импорты файла.

В `UserRepositoryTest.java` строка 19 — `new User(null, "ivan", UserStatus.ACTIVE)` →
```java
new User(null, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Строка 34 — `new User(null, "blocked-user", UserStatus.BLOCKED)` →
```java
new User(null, "blocked-user", "password123", UserRole.USER, UserStatus.BLOCKED)
```
Добавить `import org.example.filestorage.model.UserRole;`.

В `UserServiceTest.java` строки 34, 49, 73 — `new User(1, "ivan", UserStatus.ACTIVE)` →
```java
new User(1, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Строка 50 — `new User(1, "ivan", UserStatus.BLOCKED)` →
```java
new User(1, "ivan", "password123", UserRole.USER, UserStatus.BLOCKED)
```
Строка 74 — `new User(1, "ivan2", UserStatus.ACTIVE)` →
```java
new User(1, "ivan2", "password123", UserRole.USER, UserStatus.ACTIVE)
```
Добавить `import org.example.filestorage.model.UserRole;`.

В `FileServiceTest.java` строки 51, 78, 96, 122, 143 — соответствующие
`new User(null, "<имя>", UserStatus.ACTIVE)` → добавить `"password123", UserRole.USER,` перед
`UserStatus.ACTIVE`, например строка 51:
```java
User user = userRepository.save(new User(null, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE)).block();
```
(аналогично для "petr", "sidor", "big-file-user", "broken-put-user" на своих строках). Добавить
`import org.example.filestorage.model.UserRole;`.

- [ ] **Step 8: Тест на `findByUsername`**

В `UserRepositoryTest.java`:

```java
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
```

- [ ] **Step 9: Прогнать весь набор тестов**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL, все тесты зелёные (новые + существующие, скорректированные под новую
сигнатуру `User`)

- [ ] **Step 10: Grep-проверка на блокирующие вызовы**

Run: `grep -rn "\.block()" src/main/java/`
Expected: пусто (0 совпадений)

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/db/migration/V4__add_auth_fields_to_users.sql \
        src/main/java/org/example/filestorage/model/UserRole.java \
        src/main/java/org/example/filestorage/model/User.java \
        src/main/java/org/example/filestorage/dto/UserDto.java \
        src/main/java/org/example/filestorage/repository/UserRepository.java \
        src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java \
        src/test/java/org/example/filestorage/repository/EventRepositoryTest.java \
        src/test/java/org/example/filestorage/repository/UserRepositoryTest.java \
        src/test/java/org/example/filestorage/service/UserServiceTest.java \
        src/test/java/org/example/filestorage/service/FileServiceTest.java
git commit -m "Добавить password/role в User: миграция V4, findByUsername"
```

---

### Task 2: JWT — генерация и разбор токена

**Files:**
- Create: `src/main/java/org/example/filestorage/security/JwtProperties.java`
- Create: `src/main/java/org/example/filestorage/security/JwtClaims.java`
- Create: `src/main/java/org/example/filestorage/security/JwtTokenProvider.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/org/example/filestorage/FileStorageApplication.java` (добавить
  `@EnableConfigurationProperties` или убедиться, что `JwtProperties` подхватится — см. Step 1)
- Test: `src/test/java/org/example/filestorage/security/JwtTokenProviderTest.java`

**Interfaces:**
- Consumes: ничего из предыдущих задач
- Produces: `JwtTokenProvider.generateToken(Integer userId, String username, UserRole role): String`,
  `JwtTokenProvider.parseToken(String token): JwtClaims` (бросает `io.jsonwebtoken.JwtException`
  на невалидный/просроченный токен), `JwtClaims(Integer userId, String username, UserRole role)`

- [ ] **Step 1: `JwtProperties`**

```java
package org.example.filestorage.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {
}
```

- [ ] **Step 2: Дописать `application.yml`**

Заменить блок-комментарий в конце файла:
```yaml
# TODO: секция jwt появится вместе с соответствующими @ConfigurationProperties,
# когда дойдём до реализации Security — не заводить заранее вслепую
```
на:
```yaml
jwt:
  # dev-значение по умолчанию — в проде переопределяется через JWT_SECRET (env). Не короче 32
  # байт — jjwt требует минимум 256 бит ключа для HS256, иначе падает при старте
  secret: ${JWT_SECRET:dev-only-secret-key-change-me-please-32bytes-min}
  expiration-ms: ${JWT_EXPIRATION_MS:3600000}
```

- [ ] **Step 3: `JwtClaims`**

```java
package org.example.filestorage.security;

import org.example.filestorage.model.UserRole;

public record JwtClaims(Integer userId, String username, UserRole role) {
}
```

- [ ] **Step 4: `JwtTokenProvider`**

```java
package org.example.filestorage.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.filestorage.model.UserRole;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    public String generateToken(Integer userId, String username, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public JwtClaims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Integer userId = claims.get("userId", Integer.class);
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new JwtClaims(userId, claims.getSubject(), role);
    }
}
```

- [ ] **Step 5: Убедиться, что `@ConfigurationProperties`-класс подхватится**

`JwtProperties` — `record` с `@ConfigurationProperties`, без `@Component`. Как и `MinioProperties`,
он регистрируется через `@EnableConfigurationProperties` у соответствующего `@Configuration`-класса
(смотри `MinioConfig.java` — там `@EnableConfigurationProperties(MinioProperties.class)` висит на
самом конфиге). Добавить такую же аннотацию на `JwtTokenProvider`, превратив его в источник
регистрации свойства — заменить `@Component` на:

```java
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {
```

(добавить `import org.springframework.boot.context.properties.EnableConfigurationProperties;`)

- [ ] **Step 6: Тест — round-trip генерации/разбора токена**

```java
package org.example.filestorage.security;

import org.example.filestorage.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties("test-secret-key-at-least-32-bytes-long", 3600000L));

    @Test
    void generatesTokenAndParsesItBack() {
        String token = provider.generateToken(42, "ivan", UserRole.ADMIN);

        JwtClaims claims = provider.parseToken(token);

        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.username()).isEqualTo("ivan");
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void parsingExpiredTokenThrows() {
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(new JwtProperties("test-secret-key-at-least-32-bytes-long", -1000L));
        String expiredToken = expiredProvider.generateToken(1, "ivan", UserRole.USER);

        assertThatThrownBy(() -> provider.parseToken(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void parsingTokenSignedWithDifferentKeyThrows() {
        JwtTokenProvider otherProvider =
                new JwtTokenProvider(new JwtProperties("different-secret-key-at-least-32-bytes!!", 3600000L));
        String tokenFromOtherKey = otherProvider.generateToken(1, "ivan", UserRole.USER);

        assertThatThrownBy(() -> provider.parseToken(tokenFromOtherKey))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}
```

- [ ] **Step 7: Прогнать тест**

Run: `./gradlew test --tests "org.example.filestorage.security.JwtTokenProviderTest"`
Expected: BUILD SUCCESSFUL, 3 теста зелёных

- [ ] **Step 8: Прогнать весь набор**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/example/filestorage/security/ src/main/resources/application.yml \
        src/test/java/org/example/filestorage/security/
git commit -m "Добавить JwtTokenProvider: генерация и разбор JWT (jjwt 0.13.0)"
```

---

### Task 3: Хеширование пароля, UserService.create() под пароль

**Files:**
- Create: `src/main/java/org/example/filestorage/security/PasswordEncoderConfig.java`
- Modify: `src/main/java/org/example/filestorage/service/UserService.java`
- Modify: `src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java`
- Modify: `src/test/java/org/example/filestorage/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserRole` (Task 1)
- Produces: `PasswordEncoder` bean (Spring `@Bean`); `UserService.create(String username, String rawPassword): Mono<UserDto>`
  (сигнатура заменяет старую `create(String username)`)

- [ ] **Step 1: `PasswordEncoderConfig`**

```java
package org.example.filestorage.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 2: Обновить `UserService.java`**

Заменить `Mono<UserDto> create(String username);` на:
```java
    Mono<UserDto> create(String username, String rawPassword);
```

- [ ] **Step 3: Обновить `UserServiceImpl.java`**

Заменить конструктор и поле — добавить `PasswordEncoder`:
```java
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }
```

Заменить метод `create` (временную версию из Task 1) на:
```java
    @Override
    public Mono<UserDto> create(String username, String rawPassword) {
        User user = new User(null, username, passwordEncoder.encode(rawPassword), UserRole.USER, UserStatus.ACTIVE);
        return userRepository.save(user)
                .map(userMapper::toDto)
                .doOnSuccess(dto -> log.info("Юзер '{}' создан, id={}", dto.username(), dto.id()))
                .doOnError(e -> log.error("Не удалось создать юзера '{}'", username, e));
    }
```

Добавить `import org.springframework.security.crypto.password.PasswordEncoder;`.

- [ ] **Step 4: Обновить `UserServiceTest.java`**

В `setUp()` добавить реальный `PasswordEncoder` (не мок — хеширование быстрое и детерминированное
по своей сути для целей теста) и передать его в конструктор:
```java
    private UserRepository userRepository;
    private UserService userService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        userService = new UserServiceImpl(userRepository, userMapper, passwordEncoder);
    }
```

Обновить тест `createsUserWithActiveStatus`:
```java
    @Test
    void createsUserWithActiveStatus() {
        User saved = new User(1, "ivan", "hashed-value", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.create("ivan", "raw-password"))
                .expectNextMatches(dto -> dto.id().equals(1)
                        && dto.username().equals("ivan")
                        && dto.status() == UserStatus.ACTIVE)
                .verifyComplete();

        verify(userRepository).save(argThat(u ->
                u.getId() == null
                        && u.getUsername().equals("ivan")
                        && u.getStatus() == UserStatus.ACTIVE
                        && u.getRole() == UserRole.USER
                        && !u.getPassword().equals("raw-password") // захешировано, не сырой пароль
                        && passwordEncoder.matches("raw-password", u.getPassword())));
    }
```

Добавить импорты: `org.example.filestorage.model.UserRole`,
`org.springframework.security.crypto.password.PasswordEncoder`,
`org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`.

- [ ] **Step 5: Прогнать тест**

Run: `./gradlew test --tests "org.example.filestorage.service.UserServiceTest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Прогнать весь набор**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL (компиляция могла сломаться в других местах, вызывающих старую
сигнатуру `create(String)` — на этом этапе таких мест в `src/main` быть не должно, `create`
использовался только внутри `UserServiceImpl` самим собой)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/example/filestorage/security/PasswordEncoderConfig.java \
        src/main/java/org/example/filestorage/service/UserService.java \
        src/main/java/org/example/filestorage/service/impl/UserServiceImpl.java \
        src/test/java/org/example/filestorage/service/UserServiceTest.java
git commit -m "Хешировать пароль при создании юзера (BCryptPasswordEncoder)"
```

---

### Task 4: Единый обработчик ошибок (@RestControllerAdvice)

**Files:**
- Create: `src/main/java/org/example/filestorage/exception/ErrorResponse.java`
- Create: `src/main/java/org/example/filestorage/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/org/example/filestorage/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `NotFoundException`, `FileStorageUnavailableException` (уже существуют)
- Produces: `ErrorResponse(String error, int status)`; маппинг исключений → HTTP-статус (см.
  таблицу в дизайн-документе)

- [ ] **Step 1: `ErrorResponse`**

```java
package org.example.filestorage.exception;

public record ErrorResponse(String error, int status) {
}
```

- [ ] **Step 2: `GlobalExceptionHandler`**

```java
package org.example.filestorage.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage(), 404));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Некорректные данные запроса");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message, 400));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage(), 401));
    }

    @ExceptionHandler(FileStorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(FileStorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage(), 503));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String message = "Конфликт данных: " + e.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(message, 409));
    }
}
```

- [ ] **Step 3: Тест — прямой вызов методов хендлера (без поднятия всего контекста)**

```java
package org.example.filestorage.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsNotFoundExceptionTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("File", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void mapsBadCredentialsTo401() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(new BadCredentialsException("bad creds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mapsFileStorageUnavailableTo503() {
        ResponseEntity<ErrorResponse> response =
                handler.handleStorageUnavailable(new FileStorageUnavailableException("MinIO лёг", new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsDataIntegrityViolationTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("FK violation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 4: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.exception.GlobalExceptionHandlerTest"`
Expected: BUILD SUCCESSFUL, 4 теста зелёных

- [ ] **Step 5: Прогнать весь набор**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/filestorage/exception/ErrorResponse.java \
        src/main/java/org/example/filestorage/exception/GlobalExceptionHandler.java \
        src/test/java/org/example/filestorage/exception/
git commit -m "Добавить единый @RestControllerAdvice для маппинга исключений в HTTP-коды"
```

---

### Task 5: Security-цепочка — проверка JWT на всех путях

**Files:**
- Create: `src/main/java/org/example/filestorage/security/AuthenticatedUser.java`
- Create: `src/main/java/org/example/filestorage/security/JwtServerAuthenticationConverter.java`
- Create: `src/main/java/org/example/filestorage/security/JwtReactiveAuthenticationManager.java`
- Create: `src/main/java/org/example/filestorage/security/JwtAuthenticationEntryPoint.java`
- Create: `src/main/java/org/example/filestorage/security/SecurityConfig.java`
- Modify: `src/test/java/org/example/filestorage/AbstractIntegrationTest.java` (добавить
  `WebTestClient`, нужен для этого и всех последующих задач с контроллерами)
- Test: `src/test/java/org/example/filestorage/security/SecurityWebFilterChainTest.java`

**Interfaces:**
- Consumes: `JwtTokenProvider`/`JwtClaims` (Task 2), `ErrorResponse` (Task 4)
- Produces: `AuthenticatedUser(Integer userId, String username, UserRole role)` — принципал,
  доступный в контроллерах через `@AuthenticationPrincipal AuthenticatedUser`. Пути `/auth/**` и
  Swagger — открыты, всё остальное требует `Authorization: Bearer <валидный токен>`

- [ ] **Step 1: `AuthenticatedUser`**

```java
package org.example.filestorage.security;

import org.example.filestorage.model.UserRole;

public record AuthenticatedUser(Integer userId, String username, UserRole role) {
}
```

- [ ] **Step 2: `JwtServerAuthenticationConverter`** — достаёт токен из заголовка

```java
package org.example.filestorage.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }
        String token = header.substring(BEARER_PREFIX.length());
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
```

- [ ] **Step 3: `JwtReactiveAuthenticationManager`** — проверяет токен, собирает `Authentication`

```java
package org.example.filestorage.security;

import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtReactiveAuthenticationManager(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> jwtTokenProvider.parseToken(token))
                .map(claims -> {
                    AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.username(), claims.role());
                    return (Authentication) new UsernamePasswordAuthenticationToken(principal, token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
                })
                .onErrorMap(JwtException.class, e -> new BadCredentialsException("Невалидный или просроченный токен", e));
    }
}
```

- [ ] **Step 4: `JwtAuthenticationEntryPoint`** — тело 401-ответа для отсутствующего/невалидного токена

```java
package org.example.filestorage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.filestorage.exception.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new ErrorResponse("Требуется аутентификация", 401));
        } catch (Exception e) {
            body = "{\"error\":\"Требуется аутентификация\",\"status\":401}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
```

- [ ] **Step 5: `SecurityConfig`**

```java
package org.example.filestorage.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                           JwtReactiveAuthenticationManager authenticationManager) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(new JwtServerAuthenticationConverter());
        // Без этой строки провал ВНУТРИ фильтра (невалидный/просроченный токен) уходит в дефолтный
        // HttpBasicServerAuthenticationEntryPoint (401 с Basic-заголовком), а не в наш JSON-формат —
        // это отдельный путь отказа от "токена вообще нет" (тот идёт через .exceptionHandling ниже)
        authenticationWebFilter.setAuthenticationFailureHandler(
                new ServerAuthenticationEntryPointFailureHandler(new JwtAuthenticationEntryPoint()));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(spec -> spec.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
```

- [ ] **Step 6: Добавить `WebTestClient` в `AbstractIntegrationTest`**

Заменить `@SpringBootTest` на `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
и добавить поле:

```java
    @Autowired
    protected WebTestClient webTestClient;
```

(добавить `import org.springframework.test.web.reactive.server.WebTestClient;`) — нужен реальный
поднятый порт (не `MOCK`), чтобы позже честно тестировать multipart upload через настоящий HTTP.

- [ ] **Step 7: Тест — путь без токена получает 401, с валидным токеном проходит аутентификацию**

Путь `/some-random-nonexistent-path` намеренно не существует ни в одном контроллере — так тест не
зависит от контроллеров, которых на этом этапе плана ещё нет: без токена фильтр обязан отдать 401
**до** роутинга; с валидным токеном фильтр пропускает дальше, и уже роутинг честно скажет 404
(путь не существует) — то есть аутентификация состоялась.

```java
package org.example.filestorage.security;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
                .expectStatus().isNotFound(); // прошёл аутентификацию, роутинг честно не нашёл путь
    }

    // Отдельно от "токена нет вообще" — тут токен ЕСТЬ, но битый. Это другой путь отказа внутри
    // AuthenticationWebFilter (authenticationFailureHandler), не тот, что у "нет Authentication
    // вовсе" (exceptionHandling().authenticationEntryPoint()) — если в SecurityConfig не задать
    // authenticationFailureHandler явно, этот сценарий тихо уедет в дефолтный Basic-auth 401 без
    // нашего JSON-тела, а не в наш JwtAuthenticationEntryPoint.
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
                .expectStatus().is4xxClientError() // не 401 — прошёл фильтр, упал уже на валидации/логике
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }
}
```

- [ ] **Step 8: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.security.SecurityWebFilterChainTest"`
Expected: BUILD SUCCESSFUL, 4 теста зелёных

- [ ] **Step 9: Прогнать весь набор**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add src/main/java/org/example/filestorage/security/ \
        src/test/java/org/example/filestorage/AbstractIntegrationTest.java \
        src/test/java/org/example/filestorage/security/SecurityWebFilterChainTest.java
git commit -m "Настроить реактивную Security-цепочку: JWT-фильтр, /auth/** открыт, остальное защищено"
```

---

### Task 6: Auth-поток — регистрация и логин

**Files:**
- Create: `src/main/java/org/example/filestorage/dto/RegisterRequest.java`
- Create: `src/main/java/org/example/filestorage/dto/LoginRequest.java`
- Create: `src/main/java/org/example/filestorage/dto/TokenResponse.java`
- Create: `src/main/java/org/example/filestorage/service/AuthService.java`
- Create: `src/main/java/org/example/filestorage/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/org/example/filestorage/controller/AuthController.java`
- Test: `src/test/java/org/example/filestorage/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserService.create(String, String)` (Task 3), `UserRepository.findByUsername` (Task 1),
  `JwtTokenProvider` (Task 2), `PasswordEncoder` (Task 3), Security-цепочка уже пропускает `/auth/**`
  (Task 5)
- Produces: `POST /auth/register`, `POST /auth/login` — публичные HTTP-эндпоинты

- [ ] **Step 1: DTO запросов/ответа**

```java
// RegisterRequest.java
package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username обязателен") String username,
        @NotBlank(message = "password обязателен")
        @Size(min = 6, message = "пароль должен быть не короче 6 символов") String password) {
}
```

```java
// LoginRequest.java
package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username обязателен") String username,
        @NotBlank(message = "password обязателен") String password) {
}
```

```java
// TokenResponse.java
package org.example.filestorage.dto;

public record TokenResponse(String token) {
}
```

- [ ] **Step 2: `AuthService`**

```java
package org.example.filestorage.service;

import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<UserDto> register(String username, String password);

    Mono<TokenResponse> login(String username, String password);
}
```

- [ ] **Step 3: `AuthServiceImpl`**

```java
package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.security.JwtTokenProvider;
import org.example.filestorage.service.AuthService;
import org.example.filestorage.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(UserService userService, UserRepository userRepository,
                            PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<UserDto> register(String username, String password) {
        return userService.create(username, password);
    }

    @Override
    public Mono<TokenResponse> login(String username, String password) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Неверный логин или пароль")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new BadCredentialsException("Неверный логин или пароль"));
                    }
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
                    return Mono.just(new TokenResponse(token));
                })
                .doOnSuccess(dto -> log.info("Юзер '{}' залогинился", username))
                .doOnError(e -> !(e instanceof BadCredentialsException),
                        e -> log.error("Ошибка при логине юзера '{}'", username, e));
    }
}
```

- [ ] **Step 4: `AuthController`**

```java
package org.example.filestorage.controller;

import jakarta.validation.Valid;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username(), request.password());
    }

    @PostMapping("/auth/login")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
}
```

- [ ] **Step 5: Интеграционный тест — полный цикл регистрация → логин**

```java
package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.TokenResponse;
import org.example.filestorage.dto.UserDto;
import org.junit.jupiter.api.Test;

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
        webTestClient.post().uri("/auth/register")
                .bodyValue(new RegisterRequest("", "secret123"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
```

- [ ] **Step 6: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.controller.AuthControllerTest"`
Expected: BUILD SUCCESSFUL, 3 теста зелёных

- [ ] **Step 7: Прогнать весь набор + grep на блокирующие вызовы**

Run: `./gradlew clean test && grep -rn "\.block()" src/main/java/`
Expected: BUILD SUCCESSFUL, grep — пусто

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/example/filestorage/dto/RegisterRequest.java \
        src/main/java/org/example/filestorage/dto/LoginRequest.java \
        src/main/java/org/example/filestorage/dto/TokenResponse.java \
        src/main/java/org/example/filestorage/service/AuthService.java \
        src/main/java/org/example/filestorage/service/impl/AuthServiceImpl.java \
        src/main/java/org/example/filestorage/controller/AuthController.java \
        src/test/java/org/example/filestorage/controller/
git commit -m "Добавить регистрацию и логин: POST /auth/register, POST /auth/login"
```

---

### Task 7: UserController

**Files:**
- Create: `src/main/java/org/example/filestorage/dto/RenameRequest.java`
- Create: `src/main/java/org/example/filestorage/controller/UserController.java`
- Test: `src/test/java/org/example/filestorage/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserService` (уже есть), `JwtTokenProvider` (Task 2, для получения тестового токена)
- Produces: `GET /users/{id}`, `GET /users`, `PUT /users/{id}`, `DELETE /users/{id}` — все требуют
  валидный токен (см. Task 5)

- [ ] **Step 1: `RenameRequest`**

```java
package org.example.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameRequest(@NotBlank(message = "username обязателен") String username) {
}
```

- [ ] **Step 2: `UserController`**

```java
package org.example.filestorage.controller;

import jakarta.validation.Valid;
import org.example.filestorage.dto.RenameRequest;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public Mono<UserDto> getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    @GetMapping
    public Flux<UserDto> getAll() {
        return userService.getAll();
    }

    @PutMapping("/{id}")
    public Mono<UserDto> rename(@PathVariable Integer id, @Valid @RequestBody RenameRequest request) {
        return userService.rename(id, request.username());
    }

    @DeleteMapping("/{id}")
    public Mono<UserDto> delete(@PathVariable Integer id) {
        return userService.delete(id);
    }
}
```

- [ ] **Step 3: Интеграционный тест**

```java
package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.RenameRequest;
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
                .bodyValue(new org.example.filestorage.dto.LoginRequest("target-user", "secret123"))
                .exchange()
                .expectBody(org.example.filestorage.dto.TokenResponse.class)
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
                .expectStatus().isNotFound(); // заблокирован, findByIdAndStatus его больше не находит
    }
}
```

- [ ] **Step 4: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.controller.UserControllerTest"`
Expected: BUILD SUCCESSFUL, 2 теста зелёных

- [ ] **Step 5: Прогнать весь набор**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/filestorage/dto/RenameRequest.java \
        src/main/java/org/example/filestorage/controller/UserController.java \
        src/test/java/org/example/filestorage/controller/UserControllerTest.java
git commit -m "Добавить UserController: GET/PUT/DELETE /users, все под защитой токена"
```

---

### Task 8: FileController

**Files:**
- Create: `src/main/java/org/example/filestorage/controller/FileController.java`
- Test: `src/test/java/org/example/filestorage/controller/FileControllerTest.java`

**Interfaces:**
- Consumes: `FileService` (уже есть), `AuthenticatedUser` (Task 5, для `userId` при upload/delete)
- Produces: `POST /files`, `GET /files/{id}`, `GET /files/{id}/download`, `GET /files`,
  `DELETE /files/{id}` — все требуют токен

- [ ] **Step 1: `FileController`**

```java
package org.example.filestorage.controller;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.security.AuthenticatedUser;
import org.example.filestorage.service.FileService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FileDto> upload(@RequestPart("file") FilePart filePart,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return DataBufferUtils.join(filePart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(bytes -> fileService.upload(filePart.filename(), bytes, principal.userId()));
    }

    @GetMapping("/{id}")
    public Mono<FileDto> getById(@PathVariable Integer id) {
        return fileService.getById(id);
    }

    @GetMapping("/{id}/download")
    public Flux<byte[]> download(@PathVariable Integer id) {
        return fileService.getContent(id);
    }

    @GetMapping
    public Flux<FileDto> getAll() {
        return fileService.getAll();
    }

    @DeleteMapping("/{id}")
    public Mono<FileDto> delete(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return fileService.delete(id, principal.userId());
    }
}
```

- [ ] **Step 2: Интеграционный тест — upload, метаданные, скачивание, удаление**

```java
package org.example.filestorage.controller;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.dto.FileDto;
import org.example.filestorage.dto.LoginRequest;
import org.example.filestorage.dto.RegisterRequest;
import org.example.filestorage.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.MultipartBodyBuilder;

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
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(bodyBuilder.build()))
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
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 3: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.controller.FileControllerTest"`
Expected: BUILD SUCCESSFUL, 2 теста зелёных

- [ ] **Step 4: Прогнать весь набор + grep на блокирующие вызовы**

Run: `./gradlew clean test && grep -rn "\.block()" src/main/java/`
Expected: BUILD SUCCESSFUL, grep — пусто

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/filestorage/controller/FileController.java \
        src/test/java/org/example/filestorage/controller/FileControllerTest.java
git commit -m "Добавить FileController: upload, метаданные, скачивание, список, удаление"
```

---

### Task 9: EventController

**Files:**
- Create: `src/main/java/org/example/filestorage/controller/EventController.java`
- Test: `src/test/java/org/example/filestorage/controller/EventControllerTest.java`

**Interfaces:**
- Consumes: `EventService` (уже есть)
- Produces: `GET /events/{id}`, `GET /events`, `DELETE /events/{id}` — все требуют токен

- [ ] **Step 1: `EventController`**

```java
package org.example.filestorage.controller;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.service.EventService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/{id}")
    public Mono<EventDto> getById(@PathVariable Integer id) {
        return eventService.getById(id);
    }

    @GetMapping
    public Flux<EventDto> getAll() {
        return eventService.getAll();
    }

    @DeleteMapping("/{id}")
    public Mono<EventDto> delete(@PathVariable Integer id) {
        return eventService.delete(id);
    }
}
```

- [ ] **Step 2: Интеграционный тест — событие реально появляется после upload и видно через контроллер**

```java
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
```

- [ ] **Step 3: Прогнать тесты**

Run: `./gradlew test --tests "org.example.filestorage.controller.EventControllerTest"`
Expected: BUILD SUCCESSFUL, 2 теста зелёных

- [ ] **Step 4: Прогнать весь набор + grep на блокирующие вызовы**

Run: `./gradlew clean test && grep -rn "\.block()" src/main/java/`
Expected: BUILD SUCCESSFUL, grep — пусто

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/filestorage/controller/EventController.java \
        src/test/java/org/example/filestorage/controller/EventControllerTest.java
git commit -m "Добавить EventController: GET/DELETE /events, все под защитой токена"
```
