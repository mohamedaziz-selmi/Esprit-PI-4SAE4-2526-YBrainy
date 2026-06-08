package com.esprit.postservice.service;

import com.esprit.postservice.dto.PostRequest;
import com.esprit.postservice.dto.PostResponse;
import com.esprit.postservice.exception.BadRequestException;
import com.esprit.postservice.exception.ResourceNotFoundException;
import com.esprit.postservice.feign.ThreadFeignClient;
import com.esprit.postservice.feign.UserFeignClient;
import com.esprit.postservice.messaging.ForumEventPublisher;
import com.esprit.postservice.model.Post;
import com.esprit.postservice.repository.PostRepository;
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
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserFeignClient userFeignClient;
    @Mock ThreadFeignClient threadFeignClient;
    @Mock ForumEventPublisher eventPublisher;
    @InjectMocks PostService service;

    private Post sample(Long id, Long authorId, Long threadId) {
        return Post.builder()
                .id(id)
                .content("My post content")
                .authorId(authorId)
                .threadId(threadId)
                .bestAnswer(false)
                .build();
    }

    private PostRequest sampleRequest(Long authorId, Long threadId) {
        PostRequest req = new PostRequest();
        req.setBody("My post content");
        req.setAuthorId(authorId);
        req.setThreadId(threadId);
        return req;
    }

    /* ─── getAll / getByThread / getByAuthor ─── */

    @Test
    @DisplayName("getAll() returns all posts")
    void getAll_returnsList() {
        when(postRepository.findAll())
                .thenReturn(List.of(sample(1L, 10L, 100L), sample(2L, 11L, 100L)));

        assertThat(service.getAll()).hasSize(2);
    }

    @Test
    @DisplayName("getByThread() returns posts for given thread")
    void getByThread_returnsList() {
        when(postRepository.findByThreadIdOrderByCreatedAtAsc(100L))
                .thenReturn(List.of(sample(1L, 10L, 100L)));

        assertThat(service.getByThread(100L)).hasSize(1);
    }

    @Test
    @DisplayName("countByThread() delegates to repository")
    void countByThread_delegates() {
        when(postRepository.countByThreadId(100L)).thenReturn(3L);

        assertThat(service.countByThread(100L)).isEqualTo(3L);
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when post exists")
    void getById_found() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(sample(1L, 10L, 100L)));

        PostResponse result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBody()).isEqualTo("My post content");
    }

    @Test
    @DisplayName("getById() throws ResourceNotFoundException when not found")
    void getById_notFound_throws() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found");
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves post and publishes event")
    void create_success() {
        Post saved = sample(20L, 10L, 100L);
        when(postRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishPostCreated(any(), any(), any());

        PostResponse result = service.create(sampleRequest(10L, 100L));

        assertThat(result.getAuthorId()).isEqualTo(10L);
        verify(postRepository).save(any());
        verify(eventPublisher).publishPostCreated(20L, 10L, 100L);
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() patches non-null fields and saves")
    void update_patchesFields() {
        Post existing = sample(30L, 10L, 100L);
        when(postRepository.findById(30L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any())).thenReturn(existing);

        PostRequest req = new PostRequest();
        req.setBody("Updated body");

        service.update(30L, req);

        assertThat(existing.getContent()).isEqualTo("Updated body");
        verify(postRepository).save(existing);
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when post not found")
    void update_notFound_throws() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, sampleRequest(1L, 1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found");
    }

    /* ─── markBestAnswer ─── */

    @Test
    @DisplayName("markBestAnswer() sets bestAnswer=true and publishes event")
    void markBestAnswer_success() {
        Post post = sample(40L, 10L, 100L);
        when(postRepository.findById(40L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenReturn(post);
        doNothing().when(eventPublisher).publishBestAnswer(any(), any(), any());

        PostResponse result = service.markBestAnswer(40L, 10L);

        assertThat(result.isBestAnswer()).isTrue();
        verify(eventPublisher).publishBestAnswer(40L, 10L, 100L);
    }

    @Test
    @DisplayName("markBestAnswer() throws ResourceNotFoundException when post not found")
    void markBestAnswer_notFound_throws() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markBestAnswer(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes post when author matches")
    void delete_success() {
        Post post = sample(50L, 10L, 100L);
        when(postRepository.findById(50L)).thenReturn(Optional.of(post));
        doNothing().when(postRepository).delete(post);

        service.delete(50L, 10L);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("delete() throws BadRequestException when user is not the author")
    void delete_notOwner_throws() {
        Post post = sample(51L, 10L, 100L);
        when(postRepository.findById(51L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.delete(51L, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only the post owner");
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when post not found")
    void delete_notFound_throws() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found");
    }
}
