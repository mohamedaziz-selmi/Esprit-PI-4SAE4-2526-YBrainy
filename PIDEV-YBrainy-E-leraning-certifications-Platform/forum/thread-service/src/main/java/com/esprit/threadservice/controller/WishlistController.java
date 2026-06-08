package com.esprit.threadservice.controller;

import com.esprit.threadservice.dto.ThreadResponse;
import com.esprit.threadservice.service.ThreadInteractionService;
import com.esprit.threadservice.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final ThreadInteractionService interactionService;
    private final ThreadService threadService;

    @GetMapping
    public ResponseEntity<List<ThreadResponse>> getWishlist(@RequestParam Long userId) {
        List<Long> threadIds = interactionService.getWishlistThreadIds(userId);
        List<ThreadResponse> threads = threadIds.stream()
                .map(id -> {
                    try { return threadService.getById(id, userId); } catch (Exception e) { return null; }
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());
        return ResponseEntity.ok(threads);
    }
}
