# Порядок изучения источников — File Storage REST API (MinIO S3 + JWT, Spring WebFlux)

> Список источников и логика порядка — см. `PROGRESS.md` → «Источники обучения». Здесь —
> тот же список, но в согласованном порядке прохождения, с прямыми ссылками. Отмечать `[x]`
> по мере прохождения.

1. [x] [proselyte Spring tutorial (full)](https://proselyte.net/tutorials/spring-tutorial-full-version/)
   — общий фундамент: IoC, DI, бины, базовый Spring MVC. **Пройден полностью (все 19 глав)**,
   с актуализацией на 2026 — итоги в `PROGRESS.md`
2. [x] [Борисов — «Спринг Потрошитель. Ремейк: вооружённый Дебаггером»](https://www.youtube.com/watch?v=Kb82DYpaHGY)
   — **пройден**. 7 паззлов на весь конвейер Environment → BeanDefinition → BeanFactoryPostProcessor
   → создание объектов → BeanPostProcessor ×2 → init → proxy → контекст. Итоги и полная схема —
   в `notes.html`, карточки — в `review.md`.
   - Если чего-то не хватит по глубине — следующая по свежести: [«Spring-потрошитель, 12 лет
     спустя»](https://www.youtube.com/watch?v=R4-XhHmd3zc)
   - Совсем оригинал (2014), для полной технической глубины: [часть 1](https://youtu.be/BmBr5diz8WA),
     [часть 2](https://youtu.be/cou_qomYLNU)
4. [x] [JUGLviv meetup — «Spring Boot the Ripper»](https://youtu.be/8xa0RWMwAOE) — **пройден**
   (по транскрипции, с ~37 минуты). Executable jar / JarLauncher, `@EnableAutoConfiguration` →
   `AutoConfigurationImportSelector` → фильтрация через `@Conditional`, `ApplicationContextInitializer`.
   Актуализация: централизованный `spring.factories` (2017) → per-стартер
   `AutoConfiguration.imports` (Boot 2.7+). Диаграмма и итоги — в `notes.html`/`PROGRESS.md`.
5. [x] [«Создание REST приложения с использованием Spring»](https://youtu.be/GOtoXLvg_IQ) —
   **пройден** (по транскрипции, без просмотра целиком — чистая механика без Security, без
   новых концепций). Базовый CRUD: JPA repository, слоистая архитектура, `ResponseEntity`.
   Актуализация: `schema.sql`/`data.sql` → Flyway (у нас), нет DTO-слоя (entity торчит в API),
   нет `@Valid`, нет `@RestControllerAdvice` — ручные if/else на каждый метод
6. [x] [«Основы работы с Spring Security»](https://www.youtube.com/watch?v=7uxROJ1nduk) —
   **пройден** (посмотрен + разбор по транскрипции). Basic Auth, in-memory юзеры, роли vs
   permission'ы, `@PreAuthorize`, кастомные login/logout, юзеры из БД, **JWT с нуля**
   (`JwtTokenProvider`, `JwtTokenFilter`, кастомный `AuthController`). Актуализация: весь конфиг
   на `WebSecurityConfigurerAdapter` (удалён в Security 6) — переводим на `SecurityFilterChain`,
   когда дойдём до практики. Карточки — в `review.md`.
7. [x] [«Создание Spring Security REST API с использованием JWT токена»](https://youtu.be/yRnSUDx3Y8k)
   — **пройден** (по транскрипции). Полный end-to-end проект (тот же автор, что источник №5):
   Liquibase → Entity/Repository/Service → Security (`JwtUser`/`JwtUserFactory`/
   `JwtUserDetailsService`/`JwtTokenProvider`/`JwtTokenFilter extends GenericFilterBean`/
   `JwtConfigurer`) → 3 контроллера с ролевым доступом (auth/admin/users). **Прямой шаблон
   архитектуры для нашей практики** (User/Event/File) — включая soft delete через status-поле
   в `BaseEntity` (ровно как в ТЗ ментора) и DTO-паттерн под разные роли (`UserData` vs
   `AdminUserData`) → развили это в `@JsonView` вместо дублирования DTO-классов (обсуждение,
   не в видео). Актуализация: Liquibase (не Flyway — у нас Flyway), `GenericFilterBean` (не
   `OncePerRequestFilter` — тот безопаснее, гарантирует ровно один запуск на запрос),
   `WebSecurityConfigurerAdapter`. Также прочитана статья [Dan Vega — RSA/OAuth2 Resource
   Server подход](https://www.danvega.dev/blog/spring-security-jwt) как контраст к HMAC.
8. [x] [«Создание REST API с использованием Spring WebFlux и Security»](https://youtu.be/gz4KzqmOlaw)
   — **пройден** (ссылка исправлена 2026-09-03 — раньше была перепутана с Docker-видео, см.
   пункт 12). Полный проект: Spring Initializr (Gradle, Boot 3.0.6, Java 17), Postgres + R2DBC
   (реактивный драйвер) + отдельно обычный JDBC-драйвер только для Flyway, `UserEntity`/`UserDto`/
   `UserMapper` (MapStruct), самодельный `PBFDK2Encoder` (PBKDF2), генерация/валидация JWT
   (`SecurityService`, `JwtHandler`), `BearerTokenServerAuthenticationConverter` +
   `AuthenticationWebFilter` + `AuthenticationManager` (`ReactiveAuthenticationManager`),
   `WebSecurityConfig` (`SecurityWebFilterChain`), кастомные исключения + `AppErrorWebExceptionHandler`.
   Разобран сначала полностью с нуля через аналогии (без кода вообще), потом перепройден на
   реальном коде из репозитория ментора — https://github.com/proselytear/webfluxsecurity
   (клонирован в scratchpad для сверки). Актуализация: старый стиль jjwt-API (`setClaims`/
   `setSubject`/`signWith(algo, rawBytes)` вместо новых fluent-методов и `SecretKey`-объекта);
   PBKDF2 руками вместо готового `Pbkdf2PasswordEncoder`/`DelegatingPasswordEncoder`
   (или `Argon2PasswordEncoder`) из Spring Security. Главный НОВЫЙ (не JWT/Security, это уже
   было) концепт — реактивная модель WebFlux: event loop вместо потока на запрос, и почему один
   блокирующий вызов внутри реактивной цепочки топит непропорционально много трафика — закрывает
   давно отложенный вопрос из `review.md` про сравнение `startAsync`/virtual threads/WebFlux.
   Конспект — `notes.html` → «WebFlux: event loop», карточки — `review.md`.

   *(5–8: безымянные видео без узнаваемой серии/автора — заменить на более свежую версию
   неоткуда, конкретной даты записи не видно. Сверять API по ходу с официальной документацией
   Spring Security, см. `PROGRESS.md` → «Важно» — особенно `WebSecurityConfigurerAdapter` vs
   `SecurityFilterChain`.)*
9. [x] [habr — статья про REST](https://habr.com/ru/post/351890/) — **пройден** (освежили, без
   построчного разбора — уже плотно закрыто в модуле 2.4). Чек-лист без кода: существительные vs
   глаголы в путях, множественное число, версионирование (URI vs media-type в заголовке),
   пагинация через `Link`-заголовки, HTTPS+OAuth2 вместо Basic Auth, HTTP-методы
   (safety/idempotence), коды статусов. Единственный реально новый угол — идемпотентность
   `PUT`/`DELETE` vs `POST`/`PATCH` и её связь с безопасностью retry + паттерн Idempotency-Key
   (защита от двойного списания в платёжных API) — разобрали через сценарий на BNPL.
10. [x] [proselyte JUnit tutorial](https://proselyte.net/junit-tutorial/) — **пройден** (6 глав:
    Introduction/Simple Example/Architecture/API/Writing Tests/Assertions). Подтвердилось —
    смесь JUnit 4 (`@Test`/`@Before`, `org.junit.*`) и даже JUnit 3 (глава API: пакет
    `junit.framework`, `TestCase`/`TestResult`/`TestSuite`, поиск тестов по имени метода `testXxx`
    без аннотаций вообще). Разобран как тренажёр критического чтения: главная находка — класс
    бага "ложное покрытие" (тест молча не запускается, сборка зелёная), который кочует между
    версиями в разных обличьях — опечатка в префиксе `test` в JUnit 3, и по аналогии — случайно
    оставленная старая аннотация `org.junit.Test` вместо `org.junit.jupiter.api.Test` в проекте
    на JUnit 5. Актуализация: JUnit 3/4 → JUnit 5 (`@Before`→`@BeforeEach`, `@After`→`@AfterEach`,
    `org.junit.Assert`→`org.junit.jupiter.api.Assertions`, `@RunWith`→`@ExtendWith`,
    `@Ignore`→`@Disabled`). Конспект — `notes.html` → «JUnit: как фреймворк находит тесты»,
    карточки — `review.md`.
11. [x] [Baeldung — Mockito series](https://www.baeldung.com/mockito-series) — **Basics-часть
    пройдена** (4 статьи прочитаны полностью через браузер — Baeldung блокирует автофетч 403,
    расширение claude-in-chrome подключили в процессе): «Getting Started with @Mock/@Spy/
    @Captor/@InjectMocks», «Mockito's Mock Methods», «Mockito When/Then Cookbook», «Mockito
    Verify Cookbook». Разобраны: `@Mock` (пустышка) vs `@Spy` (обёртка над реальным объектом,
    `@InjectMocks` не может внедрить mock в spy — только через конструктор руками); `@Captor`;
    4 перегрузки `mock()`; `when()/doReturn()` (и почему `doReturn().when()` обязателен для void
    — компиляционное ограничение Java, не прихоть Mockito); `verify()` со всеми вариантами
    (`times`/`never`/`atLeast`/`atMost`/`inOrder`/`verifyNoMoreInteractions`); strict stubbing
    (`UnnecessaryStubbingException`, Mockito 2+ по умолчанию). Список Advanced (mock static/
    final/private, PowerMock) и Integration (Spring) частей серии — 19 статей — остались
    непройденными, вернуться при необходимости на практике. Актуализация: `initMocks()` →
    `openMocks()` (переименован). Конспект — `notes.html` (2 блока), карточки — `review.md`.
12. [x] [«Основы работы c Docker»](https://youtu.be/tnKFTVX9AQI) — **пройден вне очереди**
    (ссылка исправлена 2026-09-03 — раньше была перепутана с видео про WebFlux+Security)
    (по транскрипции целиком, до финала видео). Docker vs VM (namespaces/cgroups под капотом,
    Docker Desktop на Mac/Windows поднимает Linux-VM), image vs container, `pull`/`run --name -d`/
    `exec -it`/`ps`/`stop`/`rm`, volumes (bind mount на примере), сборка своего образа + запуск
    через `docker-compose`. Актуализация: `docker-compose` (Python, отдельный бинарник, EOL 2023)
    → `docker compose` (плагин в CLI, Go); `version: '3'` в compose-файле теперь опционален;
    bind mount vs named volume (для персистентных данных БД правильнее named volume, не bind);
    root внутри контейнера без вопроса (в 2026 ожидают непривилегированного `USER`); нет
    `.dockerignore`/multi-stage build в примере. Отдельно раскопали senior-вопросы: почему секрет
    в закоммиченном compose.yml нельзя обезопасить одним gitignore/чисткой истории (только
    ротация), и как работает встроенный DNS docker-compose для резолвинга сервисов по имени.
    Конспект — `notes.html` часть III, карточки — `review.md`.
13. [x] [«Docker и Kubernetes глазами разработчика»](https://youtu.be/jw9M137lfhI) — **пройден**
    (по транскрипции целиком, без просмотра — почти 3 часа). Docker-часть в основном повторяет
    источник №12 (образы/контейнеры/volumes/compose), новое — сетевое взаимодействие контейнеров
    (`host.docker.internal` для контейнер→хост, кастомные docker-сети с embedded DNS — тот же
    механизм, что уже разбирали с docker-compose, только руками) и ручной деплой на AWS EC2
    (Security Group, SSH, `docker pull`/`run` на сервере — мотивация для Kubernetes).
    Kubernetes-часть — полностью новая тема: архитектура (Control Plane — `kube-apiserver`/
    `etcd`/`kube-scheduler`/`kube-controller-manager`, Worker Node — `kubelet`/`kube-proxy`),
    три ключевых объекта (Pod/Deployment/Service), императивные команды vs декларативные
    YAML-манифесты (Infrastructure as Code), `emptyDir` vs `PersistentVolume`/
    `PersistentVolumeClaim`, `ConfigMap`/`Secret`. Прямая связка с уже пройденным: k8s `Secret`
    хранит значения в Base64 — та же history-ловушка ("не шифрование", ротация вместо чистки
    истории), что разбирали с docker-compose секретами. Конспект — `notes.html` (часть VI,
    новая), карточки — `review.md`.

## Логика порядка
Сначала механизм (что внутри Spring), потом Boot, потом голый REST, потом security слоями
(basics → JWT), потом реактивщина поверх готового security, потом тесты, потом докер под
готовый код.
