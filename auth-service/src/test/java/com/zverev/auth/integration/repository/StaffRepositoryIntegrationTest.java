package com.zverev.auth.integration.repository;

import com.zverev.auth.config.JpaConfiguration;
import com.zverev.shared.entity.Staff;
import com.zverev.shared.entity.enums.StaffRole;
import com.zverev.shared.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@DataJpaTest
@Import(JpaConfiguration.class)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class StaffRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("antifraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration");
    }

    @Autowired
    private StaffRepository staffRepository;

    @Test
    void shouldSaveAndFindStaffByLogin() {
        Staff staff = Staff.builder()
                .login("integration-user")
                .password("encoded-password")
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();

        Staff savedStaff = staffRepository.saveAndFlush(staff);

        assertNotNull(savedStaff.getId());

        var foundStaff = staffRepository.findByLogin("integration-user");

        assertTrue(foundStaff.isPresent());
        assertEquals(savedStaff.getId(), foundStaff.get().getId());
        assertEquals("integration-user", foundStaff.get().getLogin());
        assertEquals("encoded-password", foundStaff.get().getPassword());
        assertEquals(StaffRole.STAFF, foundStaff.get().getRole());
        assertTrue(foundStaff.get().isEnabled());
    }

    @Test
    void shouldReturnTrueWhenStaffExists() {
        Staff staff = Staff.builder()
                .login("existing-user")
                .password("encoded-password")
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();

        staffRepository.saveAndFlush(staff);

        assertTrue(staffRepository.existsByLogin("existing-user"));
    }

    @Test
    void shouldReturnFalseWhenStaffDoesNotExist() {
        assertFalse(staffRepository.existsByLogin("missing-user"));
    }
}
