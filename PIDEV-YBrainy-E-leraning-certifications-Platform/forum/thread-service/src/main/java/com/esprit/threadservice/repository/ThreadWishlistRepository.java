package com.esprit.threadservice.repository;

import com.esprit.threadservice.model.ThreadWishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThreadWishlistRepository extends JpaRepository<ThreadWishlist, Long> {
    Optional<ThreadWishlist> findByThreadIdAndUserId(Long threadId, Long userId);
    List<ThreadWishlist> findByUserId(Long userId);
    boolean existsByThreadIdAndUserId(Long threadId, Long userId);
    long countByThreadId(Long threadId);

    @Query("SELECT COUNT(w) FROM ThreadWishlist w JOIN ForumThread t ON w.thread.id = t.id WHERE t.authorId = :authorId")
    long countSavesReceivedByAuthor(@Param("authorId") Long authorId);

    @Modifying
    @Query("DELETE FROM ThreadWishlist w WHERE w.thread.id = :threadId")
    void deleteByThreadId(@Param("threadId") Long threadId);
}
