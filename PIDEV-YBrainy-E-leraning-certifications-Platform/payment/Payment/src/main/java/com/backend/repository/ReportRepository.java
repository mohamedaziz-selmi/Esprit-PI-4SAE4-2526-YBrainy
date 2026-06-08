package com.backend.repository;

import com.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByTypeOrderByCreatedAtDesc(String type);
    List<Report> findByTypeAndStatusOrderByCreatedAtDesc(String type, String status);
    List<Report> findByTypeAndCategoryOrderByCreatedAtDesc(String type, String category);
    List<Report> findByTypeAndPriorityOrderByCreatedAtDesc(String type, String priority);
    int countByType(String type);
    int countByTypeAndStatus(String type, String status);
    int countByTypeAndPriority(String type, String priority);
}

