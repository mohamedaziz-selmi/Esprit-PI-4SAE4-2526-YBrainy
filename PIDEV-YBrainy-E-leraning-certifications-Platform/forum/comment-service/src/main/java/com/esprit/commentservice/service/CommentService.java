package com.esprit.commentservice.service;

import com.esprit.commentservice.dto.AuthorDto;
import com.esprit.commentservice.dto.CommentRequest;
import com.esprit.commentservice.dto.CommentResponse;
import com.esprit.commentservice.exception.BadRequestException;
import com.esprit.commentservice.exception.ResourceNotFoundException;
import com.esprit.commentservice.feign.UserFeignClient;
import com.esprit.commentservice.messaging.ForumEventPublisher;
import com.esprit.commentservice.model.Comment;
import com.esprit.commentservice.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserFeignClient userFeignClient;
    private final ForumEventPublisher eventPublisher;

    public List<CommentResponse> getAll() {
        return commentRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public CommentResponse getById(Long id) {
        return toResponse(commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id)));
    }

    public List<CommentResponse> getByPost(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CommentResponse> getByThread(Long threadId) {
        return commentRepository.findByThreadIdOrderByCreatedAtAsc(threadId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CommentResponse> getByAuthor(Long authorId) {
        return commentRepository.findByAuthorId(authorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public long countByPost(Long postId) {
        return commentRepository.countByPostId(postId);
    }

    @Transactional
    public CommentResponse create(CommentRequest request) {
        Comment comment = Comment.builder()
                .content(request.getBody())
                .authorId(request.getAuthorId())
                .postId(request.getPostId())
                .threadId(request.getThreadId())
                .build();
        comment = commentRepository.save(comment);
        eventPublisher.publishCommentCreated(
                comment.getId(), comment.getAuthorId(), comment.getPostId(), comment.getThreadId());
        return toResponse(comment);
    }

    @Transactional
    public CommentResponse update(Long id, CommentRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
        if (request.getBody() != null) comment.setContent(request.getBody());
        return toResponse(commentRepository.save(comment));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
        if (!comment.getAuthorId().equals(userId)) {
            throw new BadRequestException("Only the comment owner can delete it");
        }
        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment) {
        AuthorDto author = null;
        try { author = userFeignClient.getUser(comment.getAuthorId()); } catch (Exception ignored) {}

        return CommentResponse.builder()
                .id(comment.getId())
                .body(comment.getContent())
                .authorId(comment.getAuthorId())
                .author(author)
                .postId(comment.getPostId())
                .threadId(comment.getThreadId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
