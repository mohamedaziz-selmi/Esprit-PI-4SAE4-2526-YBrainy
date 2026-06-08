package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.ThreadRequest;
import com.esprit.threadservice.dto.ThreadResponse;
import com.esprit.threadservice.exception.BadRequestException;
import com.esprit.threadservice.exception.ResourceNotFoundException;
import com.esprit.threadservice.feign.CategoryFeignClient;
import com.esprit.threadservice.feign.UserFeignClient;
import com.esprit.threadservice.messaging.ForumEventPublisher;
import com.esprit.threadservice.model.*;
import com.esprit.threadservice.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.LocalDateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadServiceTest {

    @Mock ThreadRepository threadRepository;
    @Mock ThreadVoteRepository voteRepository;
    @Mock ThreadReactionRepository reactionRepository;
    @Mock ThreadWishlistRepository wishlistRepository;
    @Mock UserFeignClient userFeignClient;
    @Mock CategoryFeignClient categoryFeignClient;
    @Mock ForumEventPublisher eventPublisher;
    @Mock ThreadAiService threadAiService;
    @InjectMocks ThreadService service;

    private ForumThread sample(Long id, Long authorId) {
        return ForumThread.builder()
                .id(id)
                .title("Test Thread")
                .body("Thread body")
                .authorId(authorId)
                .categoryId(1L)
                .status(ThreadStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void stubCounts(Long threadId) {
        when(voteRepository.countByThreadIdAndVoteType(threadId, VoteType.UPVOTE)).thenReturn(5L);
        when(voteRepository.countByThreadIdAndVoteType(threadId, VoteType.DOWNVOTE)).thenReturn(1L);
        when(reactionRepository.countByThreadIdAndReactionType(threadId, ReactionType.LIKE)).thenReturn(10L);
        when(reactionRepository.countByThreadIdAndReactionType(threadId, ReactionType.DISLIKE)).thenReturn(2L);
        when(wishlistRepository.existsByThreadIdAndUserId(eq(threadId), any())).thenReturn(false);
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns thread response with counts")
    void getById_found() {
        ForumThread thread = sample(1L, 10L);
        when(threadRepository.findById(1L)).thenReturn(Optional.of(thread));
        stubCounts(1L);

        ThreadResponse result = service.getById(1L, 10L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUpvoteCount()).isEqualTo(5L);
        assertThat(result.getVoteScore()).isEqualTo(4L);
    }

    @Test
    @DisplayName("getById() throws ResourceNotFoundException when not found")
    void getById_notFound_throws() {
        when(threadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thread not found");
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() returns all non-closed threads sorted by creation date")
    void getAll_returnsList() {
        ForumThread t1 = sample(1L, 10L);
        ForumThread t2 = sample(2L, 11L);
        when(threadRepository.findByStatusNot(ThreadStatus.CLOSED)).thenReturn(List.of(t1, t2));
        stubCounts(1L);
        stubCounts(2L);

        List<ThreadResponse> result = service.getAll(10L);

        assertThat(result).hasSize(2);
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves thread, publishes event, and triggers AI scoring")
    void create_success() {
        ThreadRequest req = new ThreadRequest();
        req.setTitle("My Thread");
        req.setBody("Body");
        req.setAuthorId(10L);
        req.setCategoryId(1L);

        ForumThread saved = sample(20L, 10L);
        when(threadRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishThreadCreated(any(), any(), any());
        doNothing().when(threadAiService).scoreAndSaveThread(any());
        stubCounts(20L);

        ThreadResponse result = service.create(req);

        assertThat(result.getId()).isEqualTo(20L);
        verify(eventPublisher).publishThreadCreated(20L, 10L, "Test Thread");
        verify(threadAiService).scoreAndSaveThread(20L);
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() patches non-blank fields for the owner")
    void update_success() {
        ForumThread existing = sample(30L, 10L);
        when(threadRepository.findById(30L)).thenReturn(Optional.of(existing));
        when(threadRepository.save(any())).thenReturn(existing);
        stubCounts(30L);

        ThreadRequest req = new ThreadRequest();
        req.setTitle("Updated Title");
        req.setBody("Updated body");

        service.update(30L, 10L, req);

        assertThat(existing.getTitle()).isEqualTo("Updated Title");
        verify(threadRepository).save(existing);
    }

    @Test
    @DisplayName("update() throws BadRequestException when user is not the owner")
    void update_notOwner_throws() {
        ForumThread existing = sample(31L, 10L);
        when(threadRepository.findById(31L)).thenReturn(Optional.of(existing));

        ThreadRequest req = new ThreadRequest();
        req.setTitle("Sneaky Edit");

        assertThatThrownBy(() -> service.update(31L, 99L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the thread owner");
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when thread not found")
    void update_notFound_throws() {
        when(threadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, 1L, new ThreadRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thread not found");
    }

    /* ─── updateStatus ─── */

    @Test
    @DisplayName("updateStatus() changes thread status for owner")
    void updateStatus_success() {
        ForumThread existing = sample(40L, 10L);
        when(threadRepository.findById(40L)).thenReturn(Optional.of(existing));
        when(threadRepository.save(any())).thenReturn(existing);
        stubCounts(40L);

        service.updateStatus(40L, 10L, ThreadStatus.CLOSED);

        assertThat(existing.getStatus()).isEqualTo(ThreadStatus.CLOSED);
        verify(threadRepository).save(existing);
    }

    @Test
    @DisplayName("updateStatus() throws BadRequestException when user is not the owner")
    void updateStatus_notOwner_throws() {
        ForumThread existing = sample(41L, 10L);
        when(threadRepository.findById(41L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(41L, 99L, ThreadStatus.CLOSED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the thread owner");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes thread and clears votes/reactions/wishlist")
    void delete_success() {
        ForumThread thread = sample(50L, 10L);
        when(threadRepository.findById(50L)).thenReturn(Optional.of(thread));
        doNothing().when(voteRepository).deleteByThreadId(50L);
        doNothing().when(reactionRepository).deleteByThreadId(50L);
        doNothing().when(wishlistRepository).deleteByThreadId(50L);
        doNothing().when(threadRepository).delete(thread);

        service.delete(50L, 10L);

        verify(voteRepository).deleteByThreadId(50L);
        verify(reactionRepository).deleteByThreadId(50L);
        verify(wishlistRepository).deleteByThreadId(50L);
        verify(threadRepository).delete(thread);
    }

    @Test
    @DisplayName("delete() throws BadRequestException when user is not the owner")
    void delete_notOwner_throws() {
        ForumThread thread = sample(51L, 10L);
        when(threadRepository.findById(51L)).thenReturn(Optional.of(thread));

        assertThatThrownBy(() -> service.delete(51L, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the thread owner");
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when thread not found")
    void delete_notFound_throws() {
        when(threadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thread not found");
    }
}
