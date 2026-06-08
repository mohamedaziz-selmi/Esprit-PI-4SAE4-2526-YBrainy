package com.ybrainy.partnership.repository;

import com.ybrainy.partnership.entity.Partnership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnershipRepository extends JpaRepository<Partnership, String> {

  boolean existsByEmailIgnoreCaseAndIdNot(String email, String id);

  boolean existsByEmailIgnoreCase(String email);

  Page<Partnership> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
}
