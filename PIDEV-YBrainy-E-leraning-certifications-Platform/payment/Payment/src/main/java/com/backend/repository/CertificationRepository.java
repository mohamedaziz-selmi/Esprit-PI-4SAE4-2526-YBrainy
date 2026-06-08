package com.backend.repository;

import com.backend.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, Long> {
    List<Certification> findAllByOrderByCreatedAtDesc();
    List<Certification> findByStatusOrderByCreatedAtDesc(String status);
    List<Certification> findByCategoryIgnoreCaseOrderByCreatedAtDesc(String category);
    List<Certification> findByLevelIgnoreCaseOrderByCreatedAtDesc(String level);
    int countByStatus(String status);
}

