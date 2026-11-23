package com.zverev.shared.repository;

import com.zverev.shared.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByLogin(String login);
    boolean existsByLogin(String login);
}
