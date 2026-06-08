package com.esprit.postservice.service;

import com.esprit.postservice.dto.AuthorDto;
import com.esprit.postservice.dto.PostRequest;
import com.esprit.postservice.dto.PostResponse;
import com.esprit.postservice.exception.BadRequestException;
import com.esprit.postservice.exception.ResourceNotFoundException;
import com.esprit.postservice.feign.ThreadFeignClient;
import com.esprit.postservice.feign.UserFeignClient;
import com.esprit.postservice.messaging.ForumEventPublisher;
import com.esprit.postservice.model.Post;
import com.esprit.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserFeignClient userFeignClient;
    private final ThreadFeignClient threadFeignClient;
    private final ForumEventPublisher eventPublisher;

    public List<PostResponse> getAll() {
        return postRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PostResponse> getByThread(Long threadId) {
        return postRepository.findByThreadIdOrderByCreatedAtAsc(threadId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PostResponse getById(Long id) {
        return toResponse(postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id)));
    }

    public List<PostResponse> getByAuthor(Long authorId) {
        return postRepository.findByAuthorId(authorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public long countByThread(Long threadId) {
        return postRepository.countByThreadId(threadId);
    }

    @Transactional
    public PostResponse create(PostRequest request) {
        Post post = Post.builder()
                .content(request.getBody())
                .authorId(request.getAuthorId())
                .threadId(request.getThreadId())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .build();
        post = postRepository.save(post);
        eventPublisher.publishPostCreated(post.getId(), post.getAuthorId(), post.getThreadId());
        return toResponse(post);
    }

    @Transactional
    public PostResponse update(Long id, PostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        if (request.getBody() != null) post.setContent(request.getBody());
        if (request.getMediaUrl() != null) post.setMediaUrl(request.getMediaUrl());
        if (request.getMediaType() != null) post.setMediaType(request.getMediaType());
        return toResponse(postRepository.save(post));
    }

    @Transactional
    public PostResponse markBestAnswer(Long postId, Long requesterId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.setBestAnswer(true);
        postRepository.save(post);
        eventPublisher.publishBestAnswer(post.getId(), post.getAuthorId(), post.getThreadId());
        return toResponse(post);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        if (!post.getAuthorId().equals(userId)) {
            throw new BadRequestException("Only the post owner can delete it");
        }
        postRepository.delete(post);
    }

    private PostResponse toResponse(Post post) {
        AuthorDto author = null;
        try { author = userFeignClient.getUser(post.getAuthorId()); } catch (Exception ignored) {}

        String threadTitle = null;
        try {
            ThreadFeignClient.ThreadTitleDto t = threadFeignClient.getThread(post.getThreadId());
            if (t != null) threadTitle = t.getTitle();
        } catch (Exception ignored) {}

        String mediaUrl  = post.getMediaUrl();
        String mediaType = post.getMediaType();
        boolean isImage  = mediaType != null && mediaType.startsWith("image/");
        String imageUrl  = isImage ? mediaUrl : null;
        String fileUrl   = (!isImage) ? mediaUrl : null;

        return PostResponse.builder()
                .id(post.getId())
                .body(post.getContent())
                .authorId(post.getAuthorId())
                .author(author)
                .threadId(post.getThreadId())
                .threadTitle(threadTitle)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .imageUrl(imageUrl)
                .fileUrl(fileUrl)
                .fileType(mediaType)
                .bestAnswer(post.isBestAnswer())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
