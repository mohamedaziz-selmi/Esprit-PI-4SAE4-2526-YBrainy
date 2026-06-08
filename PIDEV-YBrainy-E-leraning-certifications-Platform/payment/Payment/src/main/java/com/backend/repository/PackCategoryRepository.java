package com.backend.repository;

import com.backend.entity.PackCategory;
import com.backend.entity.enums.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackCategoryRepository extends JpaRepository<PackCategory, Long> {

    List<PackCategory> findByStatus(CategoryStatus status);

    Optional<PackCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}

