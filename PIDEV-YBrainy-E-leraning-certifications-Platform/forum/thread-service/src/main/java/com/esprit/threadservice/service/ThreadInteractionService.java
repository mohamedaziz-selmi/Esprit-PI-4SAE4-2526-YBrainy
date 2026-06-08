package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.ThreadInteractionStatus;
import com.esprit.threadservice.exception.BadRequestException;
import com.esprit.threadservice.exception.ResourceNotFoundException;
import com.esprit.threadservice.messaging.ForumEventPublisher;
import com.esprit.threadservice.model.*;
import com.esprit.threadservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThreadInteractionService {

    private final ThreadRepository threadRepository;
    private final ThreadVoteRepository voteRepository;
    private final ThreadReactionRepository reactionRepository;
    private final ThreadWishlistRepository wishlistRepository;
    private final ForumEventPublisher eventPublisher;

    @Transactional
    public ThreadInteractionStatus vote(Long threadId, Long userId, VoteType voteType) {
        ForumThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));
        if (thread.getAuthorId().equals(userId)) {
            throw new BadRequestException("Cannot vote on your own thread");
        }

        Optional<ThreadVote> existing = voteRepository.findByThreadIdAndVoterId(threadId, userId);
        boolean wasUpvote = existing.map(v -> v.getVoteType() == VoteType.UPVOTE).orElse(false);

        if (existing.isPresent()) {
            ThreadVote vote = existing.get();
            if (vote.getVoteType() == voteType) {
                voteRepository.delete(vote);
            } else {
                vote.setVoteType(voteType);
                voteRepository.save(vote);
                if (voteType == VoteType.UPVOTE) {
                    eventPublisher.publishUpvoteReceived(threadId, thread.getAuthorId(), userId);
                }
            }
        } else {
            voteRepository.save(ThreadVote.builder()
                    .thread(thread).voterId(userId).voteType(voteType).build());
            if (voteType == VoteType.UPVOTE) {
                eventPublisher.publishUpvoteReceived(threadId, thread.getAuthorId(), userId);
            }
        }

        return buildStatus(threadId, userId);
    }

    @Transactional
    public ThreadInteractionStatus react(Long threadId, Long userId, ReactionType reactionType) {
        ForumThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));
        if (thread.getAuthorId().equals(userId)) {
            throw new BadRequestException("Cannot react on your own thread");
        }

        Optional<ThreadReaction> existing = reactionRepository.findByThreadIdAndUserId(threadId, userId);
        if (existing.isPresent()) {
            ThreadReaction reaction = existing.get();
            if (reaction.getReactionType() == reactionType) {
                reactionRepository.delete(reaction);
            } else {
                reaction.setReactionType(reactionType);
                reactionRepository.save(reaction);
            }
        } else {
            reactionRepository.save(ThreadReaction.builder()
                    .thread(thread).userId(userId).reactionType(reactionType).build());
        }

        return buildStatus(threadId, userId);
    }

    @Transactional
    public ThreadInteractionStatus toggleWishlist(Long threadId, Long userId) {
        ForumThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));

        Optional<ThreadWishlist> existing = wishlistRepository.findByThreadIdAndUserId(threadId, userId);
        if (existing.isPresent()) {
            wishlistRepository.delete(existing.get());
        } else {
            wishlistRepository.save(ThreadWishlist.builder().thread(thread).userId(userId).build());
        }

        return buildStatus(threadId, userId);
    }

    public List<Long> getWishlistThreadIds(Long userId) {
        return wishlistRepository.findByUserId(userId)
                .stream().map(w -> w.getThread().getId()).collect(Collectors.toList());
    }

    public ThreadInteractionStatus getStatus(Long threadId, Long userId) {
        if (!threadRepository.existsById(threadId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        return buildStatus(threadId, userId);
    }

    private ThreadInteractionStatus buildStatus(Long threadId, Long userId) {
        Optional<ThreadVote> vote = voteRepository.findByThreadIdAndVoterId(threadId, userId);
        Optional<ThreadReaction> reaction = reactionRepository.findByThreadIdAndUserId(threadId, userId);
        boolean saved = wishlistRepository.existsByThreadIdAndUserId(threadId, userId);

        long up   = voteRepository.countByThreadIdAndVoteType(threadId, VoteType.UPVOTE);
        long down = voteRepository.countByThreadIdAndVoteType(threadId, VoteType.DOWNVOTE);
        long like = reactionRepository.countByThreadIdAndReactionType(threadId, ReactionType.LIKE);
        long dis  = reactionRepository.countByThreadIdAndReactionType(threadId, ReactionType.DISLIKE);

        return ThreadInteractionStatus.builder()
                .threadId(threadId)
                .userId(userId)
                .userVote(vote.map(v -> v.getVoteType().name()).orElse(null))
                .userReaction(reaction.map(r -> r.getReactionType().name()).orElse(null))
                .wishlisted(saved)
                .upvoteCount(up)
                .downvoteCount(down)
                .voteScore(up - down)
                .likeCount(like)
                .dislikeCount(dis)
                .build();
    }
}
