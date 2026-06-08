package com.esprit.postservice.controller;

import com.esprit.postservice.dto.PostRequest;
import com.esprit.postservice.dto.PostResponse;
import com.esprit.postservice.service.FileStorageService;
import com.esprit.postservice.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public ResponseEntity<List<PostResponse>> getAll() {
        return ResponseEntity.ok(postService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getById(id));
    }

    @GetMapping("/thread/{threadId}")
    public ResponseEntity<List<PostResponse>> getByThread(@PathVariable Long threadId) {
        return ResponseEntity.ok(postService.getByThread(threadId));
    }

    @GetMapping("/thread/{threadId}/count")
    public ResponseEntity<Long> countByThread(@PathVariable Long threadId) {
        return ResponseEntity.ok(postService.countByThread(threadId));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<List<PostResponse>> getByAuthor(@PathVariable Long authorId) {
        return ResponseEntity.ok(postService.getByAuthor(authorId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam String body,
            @RequestParam(required = false) Long authorId,
            @RequestParam Long threadId,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile file) throws IOException {
        PostRequest request = new PostRequest();
        request.setBody(body);
        request.setAuthorId(xUserId > 0 ? xUserId : authorId);
        request.setThreadId(threadId);
        MultipartFile media = image != null ? image : file;
        if (media != null && !media.isEmpty()) {
            request.setMediaUrl(fileStorageService.store(media));
            request.setMediaType(media.getContentType());
        }
        return ResponseEntity.ok(postService.create(request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> update(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam String body,
            @RequestParam(required = false) Long authorId,
            @RequestParam Long threadId,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile file) throws IOException {
        PostRequest request = new PostRequest();
        request.setBody(body);
        request.setAuthorId(xUserId > 0 ? xUserId : authorId);
        request.setThreadId(threadId);
        MultipartFile media = image != null ? image : file;
        if (media != null && !media.isEmpty()) {
            request.setMediaUrl(fileStorageService.store(media));
            request.setMediaType(media.getContentType());
        }
        return ResponseEntity.ok(postService.update(id, request));
    }

    @PatchMapping("/{id}/best-answer")
    public ResponseEntity<PostResponse> markBestAnswer(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(postService.markBestAnswer(id, effectiveUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        postService.delete(id, effectiveUserId);
        return ResponseEntity.noContent().build();
    }
}
