package com.zverev.auth.security;

import com.zverev.shared.entity.Staff;
import com.zverev.shared.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffRepository staffRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Staff staff = staffRepository.findByLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Staff not found: " + username));

        return User.builder()
                .username(staff.getLogin())
                .password(staff.getPassword())
                .roles(staff.getRole().name())
                .disabled(!staff.isEnabled())
                .build();
    }
}
