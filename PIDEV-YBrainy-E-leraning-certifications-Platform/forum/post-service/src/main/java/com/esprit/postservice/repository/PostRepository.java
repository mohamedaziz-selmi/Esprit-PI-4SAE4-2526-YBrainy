package com.esprit.postservice.repository;

import com.esprit.postservice.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByThreadIdOrderByCreatedAtAsc(Long threadId);
    List<Post> findByAuthorId(Long authorId);
    long countByThreadId(Long threadId);

    @Modifying
    @Query("DELETE FROM Post p WHERE p.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") Long threadId);
}
