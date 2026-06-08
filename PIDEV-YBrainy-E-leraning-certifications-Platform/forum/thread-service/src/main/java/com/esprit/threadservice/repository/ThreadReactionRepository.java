package com.esprit.threadservice.repository;

import com.esprit.threadservice.model.ReactionType;
import com.esprit.threadservice.model.ThreadReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThreadReactionRepository extends JpaRepository<ThreadReaction, Long> {
    Optional<ThreadReaction> findByThreadIdAndUserId(Long threadId, Long userId);
    long countByThreadIdAndReactionType(Long threadId, ReactionType reactionType);

    @Query("SELECT COUNT(r) FROM ThreadReaction r WHERE r.reactionType = :reactionType")
    long countByReactionType(@Param("reactionType") ReactionType reactionType);

    @Query("SELECT COUNT(r) FROM ThreadReaction r JOIN ForumThread t ON r.thread.id = t.id WHERE t.authorId = :authorId AND r.reactionType = :reactionType")
    long countByAuthorIdAndReactionType(@Param("authorId") Long authorId, @Param("reactionType") ReactionType reactionType);

    @Modifying
    @Query("DELETE FROM ThreadReaction r WHERE r.thread.id = :threadId")
    void deleteByThreadId(@Param("threadId") Long threadId);
}
