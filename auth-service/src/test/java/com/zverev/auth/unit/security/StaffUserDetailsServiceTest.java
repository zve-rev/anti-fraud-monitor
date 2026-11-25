package com.zverev.auth.unit.security;

import com.zverev.auth.security.StaffUserDetailsService;
import com.zverev.shared.entity.Staff;
import com.zverev.shared.entity.enums.StaffRole;
import com.zverev.shared.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserDetailsServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffUserDetailsService staffUserDetailsService;

    private Staff staff;

    @BeforeEach
    void setUp() {
        staff = Staff.builder()
                .id(1L)
                .login("test-user")
                .password("$2a$10$encodedPassword")
                .role(StaffRole.STAFF)
                .enabled(true)
                .build();
    }

    @Test
    void shouldLoadUserByUsername() {
        when(staffRepository.findByLogin("test-user"))
                .thenReturn(Optional.of(staff));

        UserDetails userDetails =
                staffUserDetailsService.loadUserByUsername("test-user");

        assertNotNull(userDetails);
        assertEquals("test-user", userDetails.getUsername());
        assertEquals("$2a$10$encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STAFF")));

        verify(staffRepository).findByLogin("test-user");
    }

    @Test
    void shouldLoadDisabledUser() {
        staff.setEnabled(false);

        when(staffRepository.findByLogin("test-user"))
                .thenReturn(Optional.of(staff));

        UserDetails userDetails =
                staffUserDetailsService.loadUserByUsername("test-user");

        assertFalse(userDetails.isEnabled());
    }

    @Test
    void shouldThrowExceptionWhenStaffDoesNotExist() {
        when(staffRepository.findByLogin("unknown-user"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> staffUserDetailsService.loadUserByUsername("unknown-user")
        );

        assertEquals("Staff not found: unknown-user", exception.getMessage());

        verify(staffRepository).findByLogin("unknown-user");
    }
}
