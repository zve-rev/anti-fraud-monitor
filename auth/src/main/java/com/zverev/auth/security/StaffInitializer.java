package com.zverev.auth.security;

import com.zverev.entity.Staff;
import com.zverev.entity.enums.StaffRole;
import com.zverev.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StaffInitializer {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            createIfAbsent("staff", "staff123");
            createIfAbsent("staff2", "staff456");
        };
    }

    private void createIfAbsent(String login, String rawPassword) {
        if (staffRepository.existsByLogin(login)) {
            return;
        }
        Staff staff = Staff.builder()
                .login(login)
                .password(passwordEncoder.encode(rawPassword))
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();
        staffRepository.save(staff);
        log.info("Created staff account: {}", login);
    }
}
