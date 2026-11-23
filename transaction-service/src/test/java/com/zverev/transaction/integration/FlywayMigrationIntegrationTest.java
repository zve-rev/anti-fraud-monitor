package com.zverev.transaction.integration;

import com.zverev.transaction.TransactionServiceApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = TransactionServiceApplication.class,
        properties = {
                "outbox.poller.initial-delay=3600000",
                "outbox.poller.fixed-delay=3600000"
        }
)
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("antifraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldApplyInitialMigrationSuccessfully() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1'
                  AND description = 'initial schema'
                  AND success = TRUE
                """,
                Integer.class
        );

        assertThat(migrationCount).isEqualTo(1);

        assertTableExists("transactions");
        assertTableExists("alerts");
        assertTableExists("staff");
        assertTableExists("outbox_events");
    }

    @Test
    void shouldNotReapplyAlreadyExecutedMigration() {
        Integer migrationsBefore = countSuccessfulMigrations();

        flyway.migrate();

        Integer migrationsAfter = countSuccessfulMigrations();

        assertThat(migrationsBefore).isEqualTo(1);
        assertThat(migrationsAfter).isEqualTo(migrationsBefore);

        Integer versionOneCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1'
                """,
                Integer.class
        );

        assertThat(versionOneCount).isEqualTo(1);
    }

    private Integer countSuccessfulMigrations() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                """,
                Integer.class
        );
    }

    private void assertTableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );

        assertThat(tableCount)
                .as("Table %s should exist", tableName)
                .isEqualTo(1);
    }
}
