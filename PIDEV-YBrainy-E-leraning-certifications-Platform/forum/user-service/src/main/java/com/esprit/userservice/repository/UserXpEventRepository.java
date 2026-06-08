package com.esprit.userservice.repository;

import com.esprit.userservice.model.UserXpEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserXpEventRepository extends JpaRepository<UserXpEvent, Long> {
    List<UserXpEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<UserXpEvent> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
    List<UserXpEvent> findTop30ByUserIdOrderByCreatedAtAsc(Long userId);
}
