package com.esprit.commentservice.service;

import com.esprit.commentservice.dto.CommentRequest;
import com.esprit.commentservice.dto.CommentResponse;
import com.esprit.commentservice.exception.BadRequestException;
import com.esprit.commentservice.exception.ResourceNotFoundException;
import com.esprit.commentservice.feign.UserFeignClient;
import com.esprit.commentservice.messaging.ForumEventPublisher;
import com.esprit.commentservice.model.Comment;
import com.esprit.commentservice.repository.CommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock UserFeignClient userFeignClient;
    @Mock ForumEventPublisher eventPublisher;
    @InjectMocks CommentService service;

    private Comment sample(Long id, Long authorId, Long postId) {
        return Comment.builder()
                .id(id)
                .content("Great post!")
                .authorId(authorId)
                .postId(postId)
                .threadId(1L)
                .build();
    }

    private CommentRequest sampleRequest(Long authorId, Long postId) {
        CommentRequest req = new CommentRequest();
        req.setBody("Great post!");
        req.setAuthorId(authorId);
        req.setPostId(postId);
        req.setThreadId(1L);
        return req;
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() returns all comments")
    void getAll_returnsList() {
        when(commentRepository.findAll())
                .thenReturn(List.of(sample(1L, 10L, 100L), sample(2L, 11L, 100L)));

        List<CommentResponse> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when comment exists")
    void getById_found() {
        when(commentRepository.findById(1L)).thenReturn(Optional.of(sample(1L, 10L, 100L)));

        CommentResponse result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBody()).isEqualTo("Great post!");
    }

    @Test
    @DisplayName("getById() throws ResourceNotFoundException when not found")
    void getById_notFound_throws() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");
    }

    /* ─── getByPost / getByThread / getByAuthor ─── */

    @Test
    @DisplayName("getByPost() returns comments ordered by creation date")
    void getByPost_returnsList() {
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(100L))
                .thenReturn(List.of(sample(1L, 10L, 100L)));

        assertThat(service.getByPost(100L)).hasSize(1);
    }

    @Test
    @DisplayName("getByThread() returns comments for given thread")
    void getByThread_returnsList() {
        when(commentRepository.findByThreadIdOrderByCreatedAtAsc(50L))
                .thenReturn(List.of(sample(2L, 11L, 200L)));

        assertThat(service.getByThread(50L)).hasSize(1);
    }

    @Test
    @DisplayName("countByPost() delegates to repository")
    void countByPost_delegates() {
        when(commentRepository.countByPostId(100L)).thenReturn(5L);

        assertThat(service.countByPost(100L)).isEqualTo(5L);
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves comment and publishes event")
    void create_success() {
        Comment saved = sample(20L, 10L, 100L);
        when(commentRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishCommentCreated(any(), any(), any(), any());

        CommentResponse result = service.create(sampleRequest(10L, 100L));

        assertThat(result.getAuthorId()).isEqualTo(10L);
        verify(commentRepository).save(any());
        verify(eventPublisher).publishCommentCreated(20L, 10L, 100L, 1L);
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() patches body and saves")
    void update_patchesBody() {
        Comment existing = sample(30L, 10L, 100L);
        when(commentRepository.findById(30L)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any())).thenReturn(existing);

        CommentRequest req = new CommentRequest();
        req.setBody("Updated body");

        service.update(30L, req);

        assertThat(existing.getContent()).isEqualTo("Updated body");
        verify(commentRepository).save(existing);
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when comment not found")
    void update_notFound_throws() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, sampleRequest(1L, 1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes comment when author matches")
    void delete_success() {
        Comment c = sample(40L, 10L, 100L);
        when(commentRepository.findById(40L)).thenReturn(Optional.of(c));
        doNothing().when(commentRepository).delete(c);

        service.delete(40L, 10L);

        verify(commentRepository).delete(c);
    }

    @Test
    @DisplayName("delete() throws BadRequestException when user is not the author")
    void delete_notOwner_throws() {
        Comment c = sample(41L, 10L, 100L);
        when(commentRepository.findById(41L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.delete(41L, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the comment owner");
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when comment not found")
    void delete_notFound_throws() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");
    }
}
