package com.zverev.auth.unit.security;

import com.zverev.auth.security.StaffInitializer;
import com.zverev.shared.entity.Staff;
import com.zverev.shared.entity.enums.StaffRole;
import com.zverev.shared.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffInitializerTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffInitializer staffInitializer;

    @Test
    void shouldCreateBothStaffAccountsWhenTheyDoNotExist() throws Exception {
        when(staffRepository.existsByLogin("staff"))
                .thenReturn(false);

        when(staffRepository.existsByLogin("staff2"))
                .thenReturn(false);

        when(passwordEncoder.encode("staff123"))
                .thenReturn("encoded-staff123");

        when(passwordEncoder.encode("staff456"))
                .thenReturn("encoded-staff456");

        CommandLineRunner runner = staffInitializer.commandLineRunner();

        runner.run();

        ArgumentCaptor<Staff> captor =
                ArgumentCaptor.forClass(Staff.class);

        verify(staffRepository, org.mockito.Mockito.times(2))
                .save(captor.capture());

        var savedStaff = captor.getAllValues();

        assertEquals(2, savedStaff.size());

        assertEquals("staff", savedStaff.get(0).getLogin());
        assertEquals("encoded-staff123", savedStaff.get(0).getPassword());
        assertEquals(StaffRole.STAFF, savedStaff.get(0).getRole());
        assertTrue(savedStaff.get(0).isEnabled());

        assertEquals("staff2", savedStaff.get(1).getLogin());
        assertEquals("encoded-staff456", savedStaff.get(1).getPassword());
        assertEquals(StaffRole.STAFF, savedStaff.get(1).getRole());
        assertTrue(savedStaff.get(1).isEnabled());

        verify(passwordEncoder).encode("staff123");
        verify(passwordEncoder).encode("staff456");
    }

    @Test
    void shouldNotCreateStaffAccountsWhenTheyAlreadyExist() throws Exception {
        when(staffRepository.existsByLogin("staff"))
                .thenReturn(true);

        when(staffRepository.existsByLogin("staff2"))
                .thenReturn(true);

        CommandLineRunner runner = staffInitializer.commandLineRunner();

        runner.run();

        verify(staffRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldCreateOnlyMissingStaffAccount() throws Exception {
        when(staffRepository.existsByLogin("staff"))
                .thenReturn(true);

        when(staffRepository.existsByLogin("staff2"))
                .thenReturn(false);

        when(passwordEncoder.encode("staff456"))
                .thenReturn("encoded-staff456");

        CommandLineRunner runner = staffInitializer.commandLineRunner();

        runner.run();

        ArgumentCaptor<Staff> captor =
                ArgumentCaptor.forClass(Staff.class);

        verify(staffRepository).save(captor.capture());

        Staff savedStaff = captor.getValue();

        assertEquals("staff2", savedStaff.getLogin());
        assertEquals("encoded-staff456", savedStaff.getPassword());
        assertEquals(StaffRole.STAFF, savedStaff.getRole());
        assertTrue(savedStaff.isEnabled());

        verify(passwordEncoder, never()).encode("staff123");
        verify(passwordEncoder).encode("staff456");
    }
}
