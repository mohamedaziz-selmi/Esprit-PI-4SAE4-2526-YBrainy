package com.backend.repository;

import com.backend.entity.CartHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartHistoryRepository extends JpaRepository<CartHistory, Long> {

    @Query(value = "SELECT * FROM cart_history WHERE user_id = :userId ORDER BY created_at DESC", nativeQuery = true)
    List<CartHistory> findByUserId(@Param("userId") Long userId);
}
