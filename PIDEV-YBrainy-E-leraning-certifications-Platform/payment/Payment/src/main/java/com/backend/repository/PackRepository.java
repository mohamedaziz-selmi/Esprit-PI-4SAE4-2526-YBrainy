package com.backend.repository;

import com.backend.entity.Pack;
import com.backend.entity.enums.PackLevel;
import com.backend.entity.enums.PackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackRepository extends JpaRepository<Pack, Long> {

    List<Pack> findByStatus(PackStatus status);

    List<Pack> findByCategoryIdAndStatus(Long categoryId, PackStatus status);

    boolean existsByCategoryIdAndStatus(Long categoryId, PackStatus status);

    long countByCategoryId(Long categoryId);

    @Query("SELECT p FROM Pack p WHERE " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:level IS NULL OR p.level = :level) AND " +
           "(:status IS NULL OR p.status = :status)")
    Page<Pack> findWithFilters(
            @Param("categoryId") Long categoryId,
            @Param("level") PackLevel level,
            @Param("status") PackStatus status,
            Pageable pageable);
}

