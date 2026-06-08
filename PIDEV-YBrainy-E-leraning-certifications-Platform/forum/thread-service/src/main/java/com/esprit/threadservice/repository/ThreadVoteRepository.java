package com.esprit.threadservice.repository;

import com.esprit.threadservice.model.ThreadVote;
import com.esprit.threadservice.model.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThreadVoteRepository extends JpaRepository<ThreadVote, Long> {
    Optional<ThreadVote> findByThreadIdAndVoterId(Long threadId, Long voterId);
    long countByThreadIdAndVoteType(Long threadId, VoteType voteType);

    @Query("SELECT COUNT(v) FROM ThreadVote v WHERE v.voteType = :voteType")
    long countByVoteType(@Param("voteType") VoteType voteType);

    @Query("SELECT COUNT(v) FROM ThreadVote v JOIN ForumThread t ON v.thread.id = t.id WHERE t.authorId = :authorId AND v.voteType = :voteType")
    long countByAuthorIdAndVoteType(@Param("authorId") Long authorId, @Param("voteType") VoteType voteType);

    @Modifying
    @Query("DELETE FROM ThreadVote v WHERE v.thread.id = :threadId")
    void deleteByThreadId(@Param("threadId") Long threadId);
}
