package com.taskforge.authservice.repository;

import com.taskforge.authservice.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmailIgnoreCase(String email);

	Optional<User> findByEmailIgnoreCase(String email);
}
