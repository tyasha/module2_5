# Прогресс модуля — File Storage REST API (MinIO S3 + JWT, Spring WebFlux)

> Это «состояние» модуля: предмет, стек, источники, что пройдено, текущая тема, практика.
> Методология (как вести обучение) — в `CLAUDE.md`. Банк вопросов — в `review.md`.
> Обновлять по ходу: «Текущая тема» — сразу после каждой завершённой темы.

## Предмет и стек
- **Предмет**: REST API поверх Spring Boot с реактивным стеком (Spring WebFlux), хранение
  файлов в объектном хранилище MinIO S3 (через AWS SDK), авторизация через JWT с ролевой
  моделью, тестирование через JUnit/Mockito/Testcontainers.
- **Технологии**: Java, Spring Boot, Spring Data JPA, Spring Security, Spring WebFlux, MySQL,
  AWS SDK (MinIO), JWT, Gradle, Flyway, Docker, JUnit, Mockito, Testcontainers, Swagger/OpenAPI.
- **Новое по сравнению с модулем 2.4** (тогда — голые Servlets, Maven, ручной Hibernate,
  MockitoExtension без Testcontainers): Spring Boot целиком, реактивный WebFlux вместо
  привычного sync/blocking MVC-подхода, Spring Security + JWT вместо самодельного `UserFilter`
  по хедеру, Gradle вместо Maven, MinIO S3 вместо локального диска, Testcontainers для
  интеграционных тестов.
- **Официальная документация** (для точного синтаксиса — сверять, т.к. часть материалов ментора
  видео и может быть не самой свежей):
  - Spring Boot: https://docs.spring.io/spring-boot/index.html
  - Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
  - Spring Security: https://docs.spring.io/spring-security/reference/index.html
  - Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/index.html
  - AWS SDK for Java v2 (S3-совместимый клиент для MinIO): https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html
  - MinIO (S3 API): https://min.io/docs/minio/linux/developers/java/minio-java.html
  - Flyway: https://documentation.red-gate.com/fd/flyway-documentation
  - Testcontainers (Java): https://java.testcontainers.org/
  - Gradle: https://docs.gradle.org/current/userguide/userguide.html
  - MySQL Reference Manual: https://dev.mysql.com/doc/refman/8.4/en/
  - OpenAPI/Swagger: https://swagger.io/docs/specification/about/
  - jjwt (или nimbus-jose-jwt, если решим на нём) — уточнить, какую JWT-библиотеку берём, при
    старте темы JWT

## Источники обучения (материалы ментора)
Основа — материалы ментора, список из ТЗ модуля (Google Doc). Текущий прогресс по ним обновлять
здесь.
1. https://proselyte.net/tutorials/spring-tutorial-full-version/ — Spring полный тьюториал
2. https://youtu.be/BmBr5diz8WA
3. https://youtu.be/cou_qomYLNU
4. https://youtu.be/8xa0RWMwAOE
5. https://youtu.be/yRnSUDx3Y8k
6. https://youtu.be/GOtoXLvg_IQ
7. https://www.youtube.com/watch?v=7uxROJ1nduk
8. https://habr.com/ru/post/351890/ — REST (уже разбирали подробно в модуле 2.4, повторно не
   штудировать построчно — освежить, если по ходу всплывёт что-то новое)
9. https://proselyte.net/junit-tutorial/
10. https://www.baeldung.com/mockito-series
11. https://youtu.be/tnKFTVX9AQI
12. https://youtu.be/gz4KzqmOlaw
13. https://youtu.be/jw9M137lfhI

**Важно (см. CLAUDE.md → «Ментор даёт старые статьи»)**: список смешанный — есть свежий
(proselyte Spring-тьюториал, полная версия — как и Tomcat-тьюториал в 2.4, вероятно актуальный)
и пачка YouTube-видео без известной даты записи — актуальность/версии Spring в них проверять по
ходу отдельно (особенно Spring Security JWT-конфигурация — API `WebSecurityConfigurerAdapter`
устарел и удалён в Spring Security 6/Boot 3, сейчас `SecurityFilterChain`-бины — частая ловушка
старых туториалов). Дата записи видео при просмотре не всегда очевидна — если встретится
`WebSecurityConfigurerAdapter`, `@EnableWebSecurity` без `SecurityFilterChain`-бина или
`spring-boot-starter-parent` версии 2.x — явно проговорить с пользователем legacy → актуальный
аналог 2026.

## Текущая тема
*(сюда — что разбираем прямо сейчас, что уже пройдено внутри темы, что осталось)*

Теория по списку выше ещё не открывалась. Согласован порядок прохождения источников (по
названиям видео, уточнённым через YouTube — в исходном списке они были без подписи) — полный
список с прямыми ссылками и чекбоксами прогресса в `sources.md`.

Источник №1 (proselyte Spring tutorial, full), главы 1–10 — прочитаны и полностью закрыты
Сократическим разбором:
- IoC-контейнер и инверсия управления, цена (потеря трассируемости) — гл. 3
- Bean scope: singleton + thread-safety mutable-полей (final не спасает — нужен stateless
  дизайн); prototype/request/session/application/custom; prototype-in-singleton ловушка
  (DI резолвится один раз при создании singleton, `@Lookup`/`ObjectProvider`/scoped-proxy —
  фиксы); prototype не получает `destroy()` от контейнера — гл. 5
- Жизненный цикл бина: конструктор→сеттеры→`@PostConstruct`, `BeanPostProcessor` как
  proxy-механизм `@Transactional`, self-invocation problem — гл. 6–7
- Наследование бинов — конфигурационное (не Java `extends`), XML-эпохи, в 2026 почти не
  используется — гл. 8
- Constructor injection vs field injection (тестируемость + `final`/fail-fast) — гл. 9
- Внедрение коллекций — Spring авто-собирает все бины подходящего типа в `List`/`Map`, Open/Closed
  принцип на практике — гл. 10
- Eager-инстанцирование всех singleton при старте `ApplicationContext` (fail-fast), в отличие от
  ленивого `BeanFactory`, `@Lazy` — гл. 1/2/4 (базовая механика)

**Источник №1 (proselyte Spring tutorial) полностью пройден — все 19 глав**, по формату
«что написано в главе → актуализация на 2026 → объяснение → контрольные вопросы» (главы 1–12 —
с вопросами и разбором ответов; главы 13–19 — по просьбе пользователя без квизов, только текст
главы + что бы дополнил).

Главные находки по актуализации (по всему тьюториалу):
- javax.* → jakarta.* (J2EE → Jakarta EE, 2017+; Spring Boot 3 требует jakarta.*) — сквозная
  тема через несколько глав (гл. 1, 6, 12)
- XML-конфигурация (`<bean>`, `ClassPathXmlApplicationContext`, `XmlBeanFactory`,
  `<context:annotation-config/>`, `<aop:config>`) — везде legacy, заменена
  аннотациями/Java-config/Boot auto-configuration
- `XmlBeanFactory` — полностью удалён из фреймворка; `autodetect` autowire-режим — deprecated
  и убран
- `@Required` — deprecated с 5.1 (2018), избыточен при constructor injection
- `DriverManagerDataSource` — не для прода (нет пулинга), в проде — HikariCP (Boot-дефолт с 2.0)
- `com.mysql.jdbc.Driver` → `com.mysql.cj.jdbc.Driver`
- `ApplicationListener`/`ApplicationEvent`-наследование → `@EventListener` + любой POJO как
  событие (с 4.2)
- **Архитектурно важно для нашего модуля**: JdbcTemplate/классический `@Transactional`
  (`PlatformTransactionManager`, ThreadLocal) — блокирующие, плохо сочетаются с WebFlux
  (реактивный аналог — R2DBC + `ReactiveTransactionManager`/`TransactionalOperator`); классический
  Spring MVC (`DispatcherServlet`, JSP/ViewResolver, `@Controller`) заменяется на WebFlux
  (`DispatcherHandler`, `@RestController`, `Mono`/`Flux`, + альтернативный функциональный стиль
  `RouterFunction`/`HandlerFunction`, которого в MVC нет вообще). Держать в уме при проектировании
  практики — трение JPA (блокирующий) + WebFlux ещё предстоит решить.

Конспект по главам 1–10 записан в `notes.html` (часть I, детальные блоки с ❓). Главы 11–19 —
итоги актуализации зафиксированы здесь, в `notes.html` пока не расписаны построчно (можно
дополнить по необходимости). Карточки по темам 1–12 — в `review.md` → «Spring IoC / DI».

**Источник №2 пройден** — Борисов «Спринг Потрошитель. Ремейк: вооружённый Дебаггером»
(пользователь смотрел видео, разбирали по транскрипции). 7 паззлов, покрывающих весь конвейер
контекста. Полная сводная схема (10 этапов, от `new` без Spring до destroy) записана в
`notes.html` → «Полный конвейер». Ключевые новые темы, которых не было в proselyte-тьюториале:
- Приоритет property-источников (command-line > env vars > профиль > application.yml),
  `EnvironmentPostProcessor` (spring.factories)
- Конвенция bean id (`@Component` без name → имя класса с маленькой буквы, кроме аббревиатур из
  2+ заглавных; `@Bean` без name → имя метода) и правила override (`@Bean` побеждает
  `@Component` с тем же id; два `@Component` с одним id — падает)
- `List<T>`-инъекция: если есть хоть один настоящий бин-кандидат, твой явно объявленный `@Bean
  List` игнорируется целиком (не мёрджится)
- Spring 7: `BeanRegistrar` — программная регистрация бинов, мощнее декларативной (кроме
  qualifier'ов)
- `BeanFactoryPostProcessor` ненадёжен для валидации на основе BeanDefinition — метаданные
  неполные и непоследовательные (у `@Bean`-бинов нет class name)
- **Главный кусок**: self-invocation + `REQUIRES_NEW` — `this.save()` внутри `saveAll()`
  игнорирует propagation целиком (транзакция одна на весь метод), try/catch не спасает
  (транзакция помечается rollback-only на уровне БД независимо от перехвата исключения в коде) —
  итог: ничего не сохраняется, а не "сохранится до первой ошибки", как интуитивно кажется
- `final`-класс + `@Transactional` — CGLIB не может унаследоваться от final, тихая поломка в
  рантайме, не ошибка компиляции. Фикс — не-final класс или работа через интерфейсы (JDK proxy)

Карточки по этим темам — в `review.md` → «Борисов «Ремейк: вооружённый Дебаггером»».

**Источник №3 пройден** — JUGLviv «Spring Boot the Ripper» (2017, Борисов + Толкачёв), разбирали
по транскрипции с ~37 минуты. Ключевые новые темы:
- **Executable jar**: манифест `Main-Class` указывает на `JarLauncher` (не на твой класс) — он
  строит classpath из вложенной `/lib` (обычный classloader не умеет лезть во вложенные jar'ы),
  твой реальный класс — отдельное поле `Start-Class`. Аналогично `WarLauncher` для war
- До Spring Boot: `web.xml` → Servlet 3.0 SPI (`ServletContainerInitializer`,
  `META-INF/services/...`, `@HandlesTypes`) → `SpringBootServletInitializer` для двойного
  режима (WAR в внешнем Tomcat / java -jar)
- **`ApplicationContextInitializer`** — находится рано (через spring.factories), но
  ВЫЗЫВАЕТСЯ позже, сразу после создания контекста, до загрузки bean definitions — площадка для
  fail-fast (пример: нет активного профиля → валим контекст)
- `ContextStartedEvent`/`ContextStoppedEvent` никогда не вызываются автоматически — только
  вручную через `context.start()`/`stop()`; после `stop()` контекстом пользоваться нельзя
- `@Component`-листенер не услышит самые ранние события старта (сканирование ещё не началось) —
  только листенер, прописанный через `spring.factories`
- **Главный кусок**: `@SpringBootApplication` = `@Configuration` + `@ComponentScan` +
  `@EnableAutoConfiguration`. Магия — не во внешней инфраструктуре (как у `@Transactional`), а
  прямо в самой аннотации через `@Import(AutoConfigurationImportSelector.class)`. Селектор
  собирает ~150-185 кандидатов auto-configuration классов со всего classpath
  (`spring.factories`/`AutoConfiguration.imports`), фильтрует через `@ConditionalOnClass` /
  `@ConditionalOnMissingBean` / `@ConditionalOnProperty` — до реальных бинов доходят единицы
- Актуализация: в 2017-м большинство auto-config классов лежали централизованно в одном
  `spring-boot-autoconfigure` джаре (нарушение Open/Closed) — с Boot 2.7+ каждый стартер
  декларирует свои в собственном `AutoConfiguration.imports`

Визуальные схемы (Артефакты, продолжают друг друга):
- [Конвейер бина](https://claude.ai/code/artifact/b2f88249-efe8-4b96-af3b-009bb301b286) —
  10 этапов от `new` до proxy в контексте, self-invocation ловушка
- [Воронка автоконфигурации](https://claude.ai/code/artifact/98758d3c-195a-412d-b25a-b2d786b0f8f6) —
  как `@EnableAutoConfiguration` фильтрует кандидатов до реальных бинов

Карточки — в `review.md` → «JUGLviv «Spring Boot the Ripper»».

**Полная цепочка запуска перепроверена сильной моделью (opus)** — найдены и исправлены в
`notes.html`: `ConfigFileApplicationListener` устарел/удалён (Boot 2.4+), реально грузит
Config Data API (`ConfigDataEnvironmentPostProcessor`); `AutoConfiguration.imports` — не просто
новая опция с 2.7, а **обязательная** замена с Boot 3.0 (`spring.factories`-ключ
`EnableAutoConfiguration` удалён); порядок внутри `prepareContext()` — Environment → initializers
→ bean definitions (не initializers → Environment, как было сказано изначально); field/`@Autowired`
инъекция — отдельный шаг `populateBean()`/`postProcessProperties()` ДО пары BeanPostProcessor
before/after, а `@PostConstruct` прячется ВНУТРИ before-фазы (не то же самое, что
`afterPropertiesSet()`/`init-method`); embedded-сервер стартует ДО `ContextRefreshedEvent`, не
после.

**Источник №4 пройден** — «Создание REST приложения с использованием Spring» (базовый CRUD-
туториал, разобран по транскрипции без просмотра — глубины/новых концепций в нём нет). Итоги
в `sources.md`. Архитектурные заметки для практики (актуальны для наших User/File/Event):
Flyway вместо `schema.sql`/`data.sql`, DTO-слой отдельно от JPA-entity, `@Valid` вместо ручных
null-проверок, `@RestControllerAdvice` вместо копипасты обработки ошибок в каждом методе
контроллера.

**Источник №6 пройден** — «Основы работы с Spring Security» (посмотрен целиком + разобран).
Basic Auth (механика, зачем нужен TLS), in-memory/DB-юзеры, роли vs permission-based доступ
(комбинаторный взрыв ролей vs фиксированные 15 атомарных прав), `@PreAuthorize`, кастомные
login/logout, и главное — **JWT с нуля**: `JwtTokenProvider` (create/validate/parse),
`JwtTokenFilter extends OncePerRequestFilter` (вставляется до
`UsernamePasswordAuthenticationFilter`), кастомный `AuthController`. Актуализация: весь конфиг
на `WebSecurityConfigurerAdapter` — при переносе в практику переводим на
`@Bean SecurityFilterChain`. Карточки — в `review.md` → «Основы работы с Spring Security».

После прохождения источника №6 углублённо разобрали Security-фундаментал с привязкой к реальному
коду BNPL (`be-lk-client/config/SecurityConfig.java`, `JwtTokenProvider.java`): аутентификация
vs авторизация, Basic Auth + роль TLS, BCrypt vs SHA-256 (медленность — намеренное свойство),
JWT подписывает (не шифрует) — payload читаем всем, access+refresh токен и зачем разделение,
JWT statelessness как архитектурное противоречие (нельзя отозвать без внешнего состояния —
в BNPL для этого Redis), роли vs permission-модель (комбинаторный взрыв 2^15 vs плоские 15
permission'ов). В BNPL увидели прод-паттерн отличный от учебного видео: не `JwtTokenFilter`, а
кастомный `SecurityContextRepository` (пустой `saveContext` — явное stateless-заявление), два
токена сразу (`accessToken` + `pinToken`/Device-Token — доп. фактор для fintech), refresh-токен
+ Redis (вероятно blacklist/revocation). Конспект — `notes.html` часть II (7 блоков), карточки —
`review.md`.

**Источник №7 пройден** — «Создание Spring Security REST API с JWT токена» (тот же автор, что
источник №5 — по транскрипции). Полный end-to-end проект: Liquibase → Entity (`BaseEntity` с
status-полем, soft delete) → Repository/Service → полноценный Security-слой (`JwtUser`,
`JwtUserFactory`, `JwtUserDetailsService`, `JwtTokenProvider`, `JwtTokenFilter extends
GenericFilterBean`, `JwtConfigurer`) → 3 контроллера с ролевым разграничением (auth/admin/users).
**Это прямой архитектурный шаблон для нашей практики User/Event/File** — включая soft delete
через status (ровно как в ТЗ ментора: ACTIVE/BLOCKED и т.п.) и DTO-по-ролям паттерн. В
обсуждении развили DTO-паттерн дальше видео: `@JsonView` вместо дублирования
`AdminXData`/`UserXData` классов под каждую роль — та же логика, что с role explosion, только
на уровне DTO-классов.

Также прочитана статья [Dan Vega — RSA/OAuth2 Resource Server](https://www.danvega.dev/blog/spring-security-jwt)
как контраст к HMAC-подходу из видео/BNPL (см. предыдущую сессию).

Актуализация: Liquibase (не Flyway — у нас Flyway в ТЗ), `GenericFilterBean` vs
`OncePerRequestFilter` (второй безопаснее — гарантирует ровно один запуск фильтра на запрос
даже при внутренних forward/include), `WebSecurityConfigurerAdapter` (как всегда, в Security 6
удалён).

После источника №7 отдельно разобрали практический вопрос: `@JsonView` элегантно решает "один
DTO под разные роли", но на практике (проверено — 0 использований в BNPL) реальные проекты чаще
выбирают явные отдельные DTO-классы (`AdminFileDto`/`UserFileDto`) — типобезопасность,
тестируемость, не смешивает domain/API-логику с ролевой, лучше композируется с вложенными
объектами. Для практики модуля (3 роли, не глубокая вложенность) — рекомендован путь явных DTO.
Блок в `notes.html` → «DTO по ролям».

**Источник №12 пройден вне очереди** — «Основы работы c Docker» (по транскрипции целиком).
Docker vs VM (в 2026 добавили то, чего в видео нет: namespaces/cgroups как реальный механизм
изоляции, и что Docker Desktop на Mac/Windows под капотом поднимает Linux-VM — миф «докер без
виртуалки» верен только на голом Linux), image vs container, база команд (`pull`/`run`/`exec -it`/
`ps`/`stop`/`rm`), volumes на примере bind mount, сборка своего образа + `docker-compose` для
multi-service приложения (web + mongo).

Актуализация: `docker-compose` (Python-бинарник через дефис) снят с поддержки в 2023 → `docker
compose` (Go-плагин в CLI, через пробел); `version: '3'` в compose-файле теперь опционален; для
персистентных данных БД правильнее named volume, а не bind mount из видео (не привязан к
конкретному пути хоста, переносится между машинами/CI); root внутри контейнера без вопроса (в
2026 ожидают `USER`-директиву); нет `.dockerignore`/multi-stage build в туториале.

Отдельно докрутили до senior двумя Сократическими вопросами (с подсказками, не с первой
попытки):
1. Секрет в закоммиченном `docker-compose.yml` — почему `.gitignore` задним числом не спасает
   (git хранит всю историю), почему чистка истории (`filter-repo`/BFG) тоже не полное решение
   (кто-то уже мог утащить копию), и что единственно надёжно — ротация самого секрета.
2. Как сервисы в compose резолвят друг друга по имени (`web` → `mongo`) — встроенный DNS-сервер
   Docker внутри созданной compose сети; тот же образ, поднятый голым `docker run` в дефолтную
   `bridge`-сеть, по имени НЕ резолвится.

Конспект — `notes.html` часть III (4 блока), карточки — `review.md` → «Основы работы c Docker».

**Исправлена путаница со ссылками (2026-09-03)**: `tnKFTVX9AQI` и `gz4KzqmOlaw` были перепутаны
местами в `sources.md` — на деле `tnKFTVX9AQI` = «Основы Docker» (это и есть уже пройденный
источник №12), а `gz4KzqmOlaw` = «Создание REST API с использованием Spring WebFlux и Security»
(оба — proselyte/Eugene Suleimanov). Ссылки в `sources.md` исправлены на верные.

**Источник №8 пройден** — «Создание REST API с использованием Spring WebFlux и Security»
(https://youtu.be/gz4KzqmOlaw, по транскрипции). Полный проект на реактивном стеке: Postgres +
R2DBC (рантайм) + отдельный обычный JDBC-драйвер только для Flyway (у R2DBC-экосистемы до сих
пор нет нативной поддержки Flyway — не баг видео, живой архитектурный шов), `UserEntity`/
`UserDto`/`UserMapper` (MapStruct), самодельный `PBFDK2Encoder`, генерация/валидация JWT
(`SecurityService`/`JwtHandler`), `BearerTokenServerAuthenticationConverter` +
`AuthenticationWebFilter` + `AuthenticationManager` (`ReactiveAuthenticationManager`),
`WebSecurityConfig` (уже современный паттерн — `@Bean SecurityWebFilterChain`, у реактивного
Security никогда не было аналога `WebSecurityConfigurerAdapter`).

Пользователь сказал "ничего не понимаю" на первый подробный пересказ — прошли материал ЗАНОВО
с нуля через бытовые аналогии (клуб/браслет/охранник/официант), без единой строчки кода, шаг за
шагом с Сократическими вопросами на каждом звене (подпись токена → реактивность/event loop →
опасность блокирующего вызова → фильтр+whitelist → заголовок Authorization: Bearer → entity/DTO
→ сервисный слой → контроллер), потом свели аналогии к точным терминам, потом перепрошли то же
самое на **реальном коде** из репозитория ментора https://github.com/proselytear/webfluxsecurity
(склонирован в scratchpad для сверки конкретных классов/строк). Хороший рабочий паттерн для
будущих сложных тем при "не понимаю" — держать в уме на будущее.

Главный НОВЫЙ концепт (не JWT/Security — то уже было пройдено раньше): реактивная модель WebFlux
— event loop с горсткой потоков вместо потока на запрос, и почему один блокирующий вызов внутри
реактивной цепочки топит непропорционально много трафика, а не только себя. Это закрывает вопрос,
отложенный ещё в модуле про HTTP/Servlets (`review.md`, карточка про `startAsync`/virtual threads)
— сравнение всех трёх подходов к одной и той же проблеме теперь завершено.

Актуализация: устаревший стиль jjwt 0.9.x API (`setClaims`/`setSubject`/`signWith(algo,rawBytes)`)
vs текущий fluent-стиль 0.12.x+ с объектом `SecretKey`; ручной PBKDF2-энкодер вместо готового
`Pbkdf2PasswordEncoder`/`DelegatingPasswordEncoder`/`Argon2PasswordEncoder` из коробки Spring
Security — сенчор-принцип "не изобретай крипто-примитивы руками, даже простые".

Конспект — `notes.html` → «WebFlux: event loop и опасность блокирующего вызова» (часть I).
Карточки — `review.md` → «Создание REST API с использованием Spring WebFlux и Security».

**Источник №9 пройден** — habr-статья про REST (освежили, без построчного разбора). Единственный
реально новый угол: идемпотентность `PUT`/`DELETE` vs `POST`/`PATCH`, связь с безопасностью
retry, паттерн **Idempotency-Key** для защиты от двойного списания в платёжных API — разобран
через сценарий на BNPL, с прямой связью к уже существующей карточке про file-orphan (тот же
механизм детерминированного ключа). Карточка — `review.md`.

**Источник №10 пройден** — proselyte JUnit tutorial (тренажёр критического чтения, как и
планировалось). Смесь JUnit 3 (глава API — `junit.framework`, `TestCase`/`TestSuite`, поиск
тестов по имени метода `testXxx`) и JUnit 4 (`@Test`/`@Before`, `org.junit.*`) внутри одного
туториала. Главная находка — класс бага "ложное покрытие тестами" (тест молча не запускается,
сборка зелёная), который проявляется по-разному в разных версиях: опечатка в префиксе `test` в
JUnit 3 vs случайно оставленная старая аннотация `org.junit.Test` вместо
`org.junit.jupiter.api.Test` в проекте на JUnit 5 — механизм разный, эффект тот же. Полная
таблица актуализации аннотаций JUnit 4/3 → 5 — в `notes.html` → «JUnit: как фреймворк находит
тесты» (часть V, новая).

**Источник №11 пройден (Basics-часть)** — Baeldung Mockito series. Baeldung блокирует автофетч
(403) — пришлось подключить расширение claude-in-chrome и читать статьи прямо в браузере
пользователя. 4 статьи: аннотации (`@Mock`/`@Spy`/`@Captor`/`@InjectMocks`, включая ограничение
"`@InjectMocks` не умеет мок в spy" и обход через ручной конструктор), перегрузки `mock()`,
`when()/doReturn()` cookbook, `verify()` cookbook. Два хороших сенчор-вопроса разобрали глубоко:
почему `doReturn().when()` обязателен для void-методов (базовое правило Java — void не value,
не компилируется иначе, не Mockito-специфика) и strict stubbing/`UnnecessaryStubbingException`
(Mockito 2+ по умолчанию, ловит мёртвые стабы после рефакторинга — тот же класс "ложное
покрытие", что разбирали с JUnit, только с другой стороны). Advanced/Integration части серии
(19 статей — mock static/final/private, PowerMock, Spring) оставлены непройденными, не
критичны для нашей практики — вернуться при необходимости. Конспект — `notes.html` (2 новых
блока в части V), карточки — `review.md`.

**Источник №13 пройден — ВСЕ 13 источников теории закрыты.** «Docker и Kubernetes глазами
разработчика» (по транскрипции, почти 3 часа). Docker-часть в основном повтор источника №12,
новое — сетевое взаимодействие контейнеров (`host.docker.internal`, кастомные сети с embedded
DNS — тот же механизм, что и docker-compose) и ручной деплой на AWS EC2. Kubernetes — полностью
новая тема: архитектура Control Plane/Worker Node, Pod/Deployment/Service, императивные команды
vs YAML-манифесты (IaC), `emptyDir` vs PersistentVolume/PVC, ConfigMap/Secret. Хорошая прямая
связка с уже пройденным материалом: k8s `Secret` хранит значения в Base64 — пользователь сам,
без подсказки, применил уже усвоенное правило (Base64 ≠ шифрование, ротация вместо чистки
истории git) к новому контексту — сигнал, что материал про секреты реально закрепился, а не
просто был озвучен один раз.

Конспект — `notes.html` (часть VI, новая: архитектура k8s + Secret/Base64). Карточки —
`review.md` → «Docker и Kubernetes глазами разработчика».

**2026-09-05/06 — повторный, более глубокий проход источника №13** по полной транскрипции видео
(раньше — по сжатому пересказу). На этот раз — с демками автора по шагам (Note App → User API →
File Service → Country API → Person/Address/Gateway), а не только конспектом слайдов. По ходу
пользователь явно попросил сменить формат на пересказ+примеры вместо непрерывного Сократического
квиза (сохранено в авто-памяти, см. `feedback_docker_review_format.md`) — часть материала прошли
в этом формате.

Новые находки, которых не было в первом проходе (добавлены в `notes.html` и `review.md`):
- Image layers/кэш сборки детальнее: BuildKit — единственный билдер с Docker 23+, считает кэш по
  контенту, `RUN --mount=type=cache`
- `localhost` внутри контейнера — это сам контейнер, не хост; `host.docker.internal` — фича
  именно Docker Desktop, на голом Linux нужен явный флаг `--add-host=host.docker.internal:host-gateway`
  (Docker 20.10+)
- Как Service физически находит поды — через labels/selectors, а не по имени Deployment
  (архитектурно важно, частый вопрос на собесе)
- PV/PVC access modes (RWO/ROX/RWX) и прямая связь с архитектурой практики: RWO ломается при
  масштабировании File-сервиса на несколько нод — отсюда объектное S3-хранилище (MinIO) вместо
  расшаренного volume в ТЗ модуля
- ConfigMap vs Secret — разница не в шифровании (оба не защищены крипто), а в контроле доступа
  через RBAC
- ClusterIP vs NodePort — принцип "не всё должно быть публичным" на примере Person (внутренний)
  vs Address/Gateway (наружу)
- Диагностика "нужно больше реплик или это баг": CPU под нагрузкой vs CPU простаивает при
  просевшем throughput — второе означает баг (блокирующий вызов и т.п.), не нехватку мощности;
  HPA слепо доверяет метрике CPU и может просто наплодить одинаково зависающих копий
- Живой пример на рабочем Rancher-кластере (BNPL, namespace sputnik-dev-1): колонки Ready/Up To
  Date/Available/Restarts/Health, реальный сервис `be-loan` с `0/0` (сознательно заглушённый
  Deployment), `be-gateway` с 5 рестартами как живое доказательство self-healing, GUI-кнопка
  Scale как аналог `kubectl scale`

## Теория завершена — переход к практике

Все 13 источников из `sources.md` пройдены. Следующий шаг по методологии (см. `CLAUDE.md`) —
разметка модели данных для практики (User/Event/File REST API, см. раздел «Практика» ниже:
сущности, DDL от ментора, уровни доступа), а затем сама реализация. Подход к архитектуре
пользователь формулирует сам (обсуждение словами вслух до кода — см. «Работа с кодом практики»
в CLAUDE.md), опираясь на прямые архитектурные шаблоны из источников №5-8 (Liquibase→Flyway,
JwtTokenProvider/JwtTokenFilter/SecurityConfig, DTO-паттерн, soft delete через status, реактивный
стек WebFlux+R2DBC) — ничего из этого не подсказывать заранее, пока пользователь не предложит
подход сам.

## Практика

### Задача от ментора
Реализовать REST API, обеспечивающий доступ к файловому хранилищу MinIO S3: загрузка/управление
файлами, история загрузок (Event), разграничение прав доступа через JWT.

**Сущности**:
- `User` — id (Integer), username (String), status (enum: ACTIVE, BLOCKED), список Event
- `Event` — id (Integer), User, File, status (enum: CREATED, UPDATED, DELETED), timestamp (LocalDateTime)
- `File` — id (Integer), name (String), location (String — MinIO S3 URL), status (enum: ACTIVE, ARCHIVED)

**Функциональные требования**:
- CRUD для User, Event, File
- При каждой загрузке файла автоматически создаётся Event (по аналогии с модулем 2.4, где
  upload создавал File+Event одной транзакцией)
- Архитектура — Spring WebFlux (реактивный стек)
- Хранение файлов — MinIO, через AWS SDK
- Авторизация — JWT, с разграничением прав
- Инициализация/миграция схемы — Flyway
- ORM — Spring Data JPA + Hibernate
- Сборка — Gradle
- Докеризация приложения
- Тесты — JUnit, Mockito, Testcontainers (интеграционные)
- Документация API — Swagger/OpenAPI

**Уровни доступа**:
- `ADMIN` — полный доступ ко всем данным и операциям
- `MODERATOR` — права `USER` + чтение всех User, чтение/изменение/удаление всех Event и File
- `USER` — только чтение своих данных и загрузка файлов за себя

**DDL от ментора** (в доке даны как ориентир — на MySQL, без явного разделения на Flyway-версии;
разбить на отдельные `V*__*.sql`-миграции по аналогии с модулем 2.4, когда дойдём до практики):
```sql
CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL
);

CREATE TABLE files (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  location VARCHAR(500) NOT NULL,
  status VARCHAR(50) NOT NULL
);

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
(Уже видно потенциальные грабли по аналогии с 2.4: инлайновый `REFERENCES` в MySQL синтаксически
проходит, но не всегда создаёт настоящий FK — разбирать на практике, а не сейчас.)

**Sequence diagram от ментора** (поток загрузки файла, для ориентира):
`User → API: JWT-запрос на upload → SecurityFilter.validateToken() → FileController →
FileService.uploadToS3() → AWS SDK.putObject() → FileService → FileRepository.persistFile() →
EventService.createEvent() → EventRepository.persistEvent() → ответ`.

**Черновик OpenAPI от ментора**: набросок на `POST /files` (multipart upload) и
`GET /files/{id}`, с `bearerAuth`/JWT в security. Полную спеку писать на практике под реальное
API, этот набросок — просто ориентир по духу задачи, не итоговый контракт.

### Статус практики
Теория полностью закрыта (все 13 источников). Практика начата с обсуждения архитектуры (словами,
до кода) — код ещё не писали.

**Архитектурное решение №1 (2026-09-06) — WebFlux vs Spring Data JPA/Hibernate, реальный конфликт
в ТЗ.** ТЗ требует одновременно «Spring WebFlux (реактивный стек)» и «ORM — Spring Data JPA +
Hibernate» — а Hibernate принципиально блокирующий, чистого нереактивного режима у него нет.
Пользователь сам дошёл до этого противоречия и сам предложил решение (без подсказки): взять
реактивный доступ к БД (R2DBC — по аналогии с источником №8, Country API) вместо Spring Data JPA,
сознательно отступив от буквы ТЗ ради честной end-to-end реактивности. **Вопрос ментору
зафиксирован, но не блокирует работу**: «WebFlux и JPA/Hibernate физически несовместимы (JPA
всегда блокирующий) — эта комбинация в ТЗ выбрана сознательно с расчётом на какой-то компромисс,
или недосмотр, и что для практики важнее — реактивность до конца или именно Hibernate как ORM?»
Задать при следующей возможности; пометку об этом отступлении от ТЗ также вынести в README
практического проекта, когда он появится.

Технические следствия решения (уже разобраны в источнике №8, применимо к практике):
- Repository не `JpaRepository`, а `ReactiveCrudRepository`/`R2dbcRepository`, методы возвращают
  `Mono`/`Flux`
- Entity/DTO/Mapper-слой остаётся, просто сервисный слой тоже реактивный сквозным образом
- Flyway у R2DBC нет нативной поддержки — отдельный обычный блокирующий JDBC-драйвер только для
  миграций при старте, рантайм-запросы через R2DBC

Дальше — сборка скелета проекта (Gradle, зависимости) и разметка модели (Entity/миграции) —
подход к структуре пользователь ещё не формулировал, не подсказывать заранее.

## Пройденные темы
*(детальный журнал по темам — заполняется по ходу)*

*(пока пусто)*

## Заметки
*(технические детали окружения, версии, решения по инфраструктуре — актуализировать,
устаревшее вычищать)*

- Проект — отдельный модуль на Gradle (не Maven, как в 2.2–2.4) — обратить внимание на другой
  синтаксис сборки (`build.gradle`/`build.gradle.kts`, `settings.gradle`) и другой набор команд
  (`./gradlew` вместо `mvn`).
- БД — вероятно MySQL, как в 2.4 (в ТЗ ментора DDL дан в синтаксисе MySQL: `AUTO_INCREMENT`,
  нет `SEQUENCE`) — подтвердить по ходу, не Postgres, как в 2.3.
- MinIO — S3-совместимое хранилище, но не сам AWS S3: при работе с AWS SDK потребуется указать
  кастомный `endpointOverride` на MinIO вместо реального AWS-региона — типичная точка, где ломают
  туториалы, писанные под настоящий S3.

**2026-09-05 — исправлена неточность в конспекте**: "final-класс ломает @Transactional" был
неверно описан как "тихая логическая поломка в рантайме" — на самом деле CGLIB не может создать
подкласс final-класса и Spring падает с `AopConfigException` прямо при старте `ApplicationContext`
(честный fail-fast, не молчаливый баг). Пользователь усомнился во время большого интервью по
модулю, сославшись на то, что в видео всё работало нормально — сверка показала, что видео
демонстрировало final-кейс отдельным теоретическим паззлом, а не как рабочий сценарий. Поправлено
в `notes.html` (`#final-proxy`) и `review.md`.

**2026-09-05 — начато большое интервью по всему модулю** (по просьбе пользователя, формат:
вопрос → ответ → докрутка → полный разбор, с нарастанием сложности от первого источника к
последнему). Перед этим пользователь прислал реальные транскрипции звонков своего ментора
(module2_4: mock-собес с Эльнаром + разбор HTTP/Servlets; module2_3: JDBC/транзакции/Mockito;
module3_1: постановка задачи + код-ревью с Игорем) — разобрали стиль: сценарии вместо абстракций,
докрутка одного факта разными гранями без подсказок, прямое называние расплывчатых ответов,
ловля путаницы соседних механизмов, code review через "почему так"/"как лучше", в конце — чёткий
пунш-лист. Пройдено пока: DI vs интерфейс/Dependency Inversion (Mockito 5+ мокает конкретные
классы — устаревший аргумент "интерфейс нужен для тестируемости"), `@Order` влияет на сборку
`List<T>` при инъекции, а не на порядок создания бинов в контейнере (частая путаница), JWT-подпись
пересчитывается заново на каждый запрос (не хранится копия оригинала), `Mono.error()` vs `throw`
в реактивной цепочке (нельзя синхронно кидать исключение из метода, возвращающего Mono/Flux),
self-invocation + `try/catch` не спасает транзакцию от отката (JPA помечает rollback-only
независимо от перехвата исключения в коде), final-класс + `@Transactional` (см. выше). Продолжать
в том же формате — следующий шаг после Spring: Security/JWT, WebFlux, Docker, K8s, JUnit/Mockito.
