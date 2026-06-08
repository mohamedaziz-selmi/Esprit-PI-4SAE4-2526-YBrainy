package com.esprit.commentservice.repository;

import com.esprit.commentservice.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<Comment> findByThreadIdOrderByCreatedAtAsc(Long threadId);
    List<Comment> findByAuthorId(Long authorId);
    long countByPostId(Long postId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") Long threadId);
}
