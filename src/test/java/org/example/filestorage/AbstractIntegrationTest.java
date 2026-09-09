package org.example.filestorage;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.mysql.MySQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

    protected static final String TEST_BUCKET = "test-bucket";

    static final MySQLContainer MYSQL;
    static final MinIOContainer MINIO;

    static {
        MYSQL = new MySQLContainer("mysql:8.4");
        MYSQL.start();

        MINIO = new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z");
        MINIO.start();

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

    @Autowired
    protected S3AsyncClient s3AsyncClient;

    @Autowired
    protected WebTestClient webTestClient;

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

    // Тот же общий-контейнер-на-весь-класс эффект, что и с БД, только для MinIO — объекты копятся
    // в бакете между тестами/классами. Удаляем по одному объекту, не батчем: batch-API
    // (deleteObjects) у этой связки SDK+MinIO падает на "Missing required header: Content-Md5"
    // (проверено вживую, не гадали) — цена на тестовой уборке не критична.
    @AfterEach
    void cleanBucket() {
        List<String> keys = s3AsyncClient.listObjectsV2(b -> b.bucket(TEST_BUCKET)).join()
                .contents().stream()
                .map(S3Object::key)
                .toList();

        keys.forEach(key -> s3AsyncClient.deleteObject(b -> b.bucket(TEST_BUCKET).key(key)).join());
    }
}
