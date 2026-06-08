package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.*;
import com.esprit.threadservice.exception.BadRequestException;
import com.esprit.threadservice.exception.ResourceNotFoundException;
import com.esprit.threadservice.feign.CategoryFeignClient;
import com.esprit.threadservice.feign.UserFeignClient;
import com.esprit.threadservice.messaging.ForumEventPublisher;
import com.esprit.threadservice.model.*;
import com.esprit.threadservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadService {

    private final ThreadRepository threadRepository;
    private final ThreadVoteRepository voteRepository;
    private final ThreadReactionRepository reactionRepository;
    private final ThreadWishlistRepository wishlistRepository;
    private final UserFeignClient userFeignClient;
    private final CategoryFeignClient categoryFeignClient;
    private final ForumEventPublisher eventPublisher;
    private final ThreadAiService threadAiService;

    public List<ThreadResponse> getAll(Long currentUserId) {
        return threadRepository.findByStatusNot(ThreadStatus.CLOSED)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(t -> toResponse(t, currentUserId))
                .collect(Collectors.toList());
    }

    public ThreadResponse getById(Long id, Long currentUserId) {
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + id));
        return toResponse(thread, currentUserId);
    }

    public List<ThreadResponse> getByCategory(Long categoryId, Long currentUserId) {
        return threadRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId)
                .stream().map(t -> toResponse(t, currentUserId)).collect(Collectors.toList());
    }

    public List<ThreadResponse> getByAuthor(Long authorId, Long currentUserId) {
        return threadRepository.findByAuthorIdOrderByCreatedAtDesc(authorId)
                .stream().map(t -> toResponse(t, currentUserId)).collect(Collectors.toList());
    }

    @Transactional
    public ThreadResponse create(ThreadRequest request) {
        ForumThread thread = ForumThread.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .authorId(request.getAuthorId())
                .categoryId(request.getCategoryId())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .status(ThreadStatus.OPEN)
                .build();
        thread = threadRepository.save(thread);
        eventPublisher.publishThreadCreated(thread.getId(), thread.getAuthorId(), thread.getTitle());
        // Trigger async AI scoring — runs in background, does not block response
        final Long savedId = thread.getId();
        threadAiService.scoreAndSaveThread(savedId);
        return toResponse(thread, request.getAuthorId());
    }

    @Transactional
    public ThreadResponse update(Long id, Long userId, ThreadRequest request) {
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + id));
        if (!thread.getAuthorId().equals(userId)) {
            throw new BadRequestException("Only the thread owner can edit it");
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) thread.setTitle(request.getTitle());
        if (request.getBody() != null) thread.setBody(request.getBody());
        if (request.getCategoryId() != null) thread.setCategoryId(request.getCategoryId());
        if (request.getMediaUrl() != null) thread.setMediaUrl(request.getMediaUrl());
        if (request.getMediaType() != null) thread.setMediaType(request.getMediaType());
        return toResponse(threadRepository.save(thread), userId);
    }

    @Transactional
    public ThreadResponse updateStatus(Long id, Long userId, ThreadStatus newStatus) {
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + id));
        if (!thread.getAuthorId().equals(userId)) {
            throw new BadRequestException("Only the thread owner can change status");
        }
        thread.setStatus(newStatus);
        return toResponse(threadRepository.save(thread), userId);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + id));
        if (!thread.getAuthorId().equals(userId)) {
            throw new BadRequestException("Only the thread owner can delete it");
        }
        voteRepository.deleteByThreadId(id);
        reactionRepository.deleteByThreadId(id);
        wishlistRepository.deleteByThreadId(id);
        threadRepository.delete(thread);
    }

    private ThreadResponse toResponse(ForumThread thread, Long currentUserId) {
        AuthorDto author = null;
        try { author = userFeignClient.getUser(thread.getAuthorId()); } catch (Exception ignored) {}

        CategoryDto category = null;
        if (thread.getCategoryId() != null) {
            try { category = categoryFeignClient.getCategory(thread.getCategoryId()); } catch (Exception ignored) {}
        }

        long upvotes   = voteRepository.countByThreadIdAndVoteType(thread.getId(), VoteType.UPVOTE);
        long downvotes = voteRepository.countByThreadIdAndVoteType(thread.getId(), VoteType.DOWNVOTE);
        long likes     = reactionRepository.countByThreadIdAndReactionType(thread.getId(), ReactionType.LIKE);
        long dislikes  = reactionRepository.countByThreadIdAndReactionType(thread.getId(), ReactionType.DISLIKE);
        boolean saved  = currentUserId != null && wishlistRepository.existsByThreadIdAndUserId(thread.getId(), currentUserId);

        // Map mediaUrl/mediaType → imageUrl/fileUrl/fileType for Angular
        String mediaUrl  = thread.getMediaUrl();
        String mediaType = thread.getMediaType();
        boolean isImage  = mediaType != null && mediaType.startsWith("image/");
        String imageUrl  = isImage ? mediaUrl : null;
        String fileUrl   = (!isImage) ? mediaUrl : null;
        String fileType  = mediaType;

        return ThreadResponse.builder()
                .id(thread.getId())
                .title(thread.getTitle())
                .body(thread.getBody())
                .authorId(thread.getAuthorId())
                .author(author)
                .categoryId(thread.getCategoryId())
                .category(category)
                .status(thread.getStatus().name())
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .imageUrl(imageUrl)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .upvoteCount(upvotes)
                .downvoteCount(downvotes)
                .voteScore(upvotes - downvotes)
                .likeCount(likes)
                .dislikeCount(dislikes)
                .savedByCurrentUser(saved)
                .createdAt(thread.getCreatedAt())
                .updatedAt(thread.getUpdatedAt())
                .aiAnalyzed(thread.isAiAnalyzed())
                .aiOverallScore(thread.getAiOverallScore())
                .aiLabel(thread.getAiLabel())
                .aiLabelColor(thread.getAiLabelColor())
                .aiSummary(thread.getAiSummary())
                .build();
    }
}
