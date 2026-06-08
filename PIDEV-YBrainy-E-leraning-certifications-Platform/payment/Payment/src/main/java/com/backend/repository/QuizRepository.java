package com.backend.repository;

import com.backend.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findAllByOrderByCreatedAtDesc();
    List<Quiz> findByCertification_IdOrderByCreatedAtDesc(Long certificationId);
    List<Quiz> findByStatusOrderByCreatedAtDesc(String status);
    int countByStatus(String status);
    int countByCertification_Id(Long certificationId);
}

