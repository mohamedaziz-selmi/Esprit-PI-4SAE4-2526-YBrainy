package com.esprit.threadservice.repository;

import com.esprit.threadservice.model.ForumThread;
import com.esprit.threadservice.model.ThreadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThreadRepository extends JpaRepository<ForumThread, Long> {
    List<ForumThread> findAllByOrderByCreatedAtDesc();
    List<ForumThread> findByCategoryIdOrderByCreatedAtDesc(Long categoryId);
    List<ForumThread> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
    List<ForumThread> findByStatusNot(ThreadStatus status);

    @Query("SELECT t FROM ForumThread t WHERE LOWER(t.title) LIKE LOWER(CONCAT('%',:kw,'%')) OR LOWER(t.body) LIKE LOWER(CONCAT('%',:kw,'%'))")
    List<ForumThread> searchByKeyword(@Param("kw") String kw, Pageable pageable);
}
