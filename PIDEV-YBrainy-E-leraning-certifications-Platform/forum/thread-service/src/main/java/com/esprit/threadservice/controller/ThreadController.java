package com.esprit.threadservice.controller;

import com.esprit.threadservice.dto.ThreadRequest;
import com.esprit.threadservice.dto.ThreadResponse;
import com.esprit.threadservice.model.ThreadStatus;
import com.esprit.threadservice.service.FileStorageService;
import com.esprit.threadservice.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/threads")
@RequiredArgsConstructor
public class ThreadController {

    private final ThreadService threadService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public ResponseEntity<List<ThreadResponse>> getAll(
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(threadService.getAll(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThreadResponse> getById(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(threadService.getById(id, userId));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ThreadResponse>> getByCategory(
            @PathVariable Long categoryId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(threadService.getByCategory(categoryId, userId));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<List<ThreadResponse>> getByAuthor(
            @PathVariable Long authorId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(threadService.getByAuthor(authorId, userId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ThreadResponse> create(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam String title,
            @RequestParam String body,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile file) throws IOException {
        ThreadRequest request = new ThreadRequest();
        request.setTitle(title);
        request.setBody(body);
        request.setAuthorId(xUserId > 0 ? xUserId : authorId);
        request.setCategoryId(categoryId);
        MultipartFile media = image != null ? image : file;
        if (media != null && !media.isEmpty()) {
            request.setMediaUrl(fileStorageService.store(media));
            request.setMediaType(media.getContentType());
        }
        return ResponseEntity.ok(threadService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThreadResponse> update(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId,
            @RequestBody ThreadRequest request) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(threadService.update(id, effectiveUserId, request));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ThreadResponse> lock(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(threadService.updateStatus(id, effectiveUserId, ThreadStatus.LOCKED));
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<ThreadResponse> unlock(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(threadService.updateStatus(id, effectiveUserId, ThreadStatus.OPEN));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<ThreadResponse> close(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(threadService.updateStatus(id, effectiveUserId, ThreadStatus.CLOSED));
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<ThreadResponse> reopen(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        return ResponseEntity.ok(threadService.updateStatus(id, effectiveUserId, ThreadStatus.OPEN));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long xUserId,
            @RequestParam(required = false) Long userId) {
        long effectiveUserId = xUserId > 0 ? xUserId : (userId != null ? userId : 0L);
        threadService.delete(id, effectiveUserId);
        return ResponseEntity.noContent().build();
    }
}
