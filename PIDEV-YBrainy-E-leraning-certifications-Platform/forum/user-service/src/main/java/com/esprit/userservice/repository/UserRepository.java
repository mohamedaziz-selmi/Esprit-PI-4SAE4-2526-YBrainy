package com.esprit.userservice.repository;

import com.esprit.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findTop10ByOrderByXpDesc();
    List<User> findAllByOrderByXpDesc();

    @Modifying
    @Transactional
    @Query(value = """
            INSERT IGNORE INTO users
                (id, username, email, password, role, xp, level, streak_days, created_at)
            VALUES
                (:id, :username, :email, 'external-user', 'STUDENT', 0, 1, 0, NOW())
            """, nativeQuery = true)
    void insertPlaceholderUser(
            @Param("id") Long id,
            @Param("username") String username,
            @Param("email") String email
    );
}
