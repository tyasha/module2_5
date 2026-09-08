package org.example.filestorage;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final MySQLContainer MYSQL;

    static {
        MYSQL = new MySQLContainer("mysql:8.4");
        MYSQL.start();
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
