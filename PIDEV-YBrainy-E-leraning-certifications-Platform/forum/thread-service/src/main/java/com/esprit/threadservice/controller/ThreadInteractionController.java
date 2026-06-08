package com.esprit.threadservice.controller;

import com.esprit.threadservice.dto.ReactRequest;
import com.esprit.threadservice.dto.ThreadInteractionStatus;
import com.esprit.threadservice.dto.VoteRequest;
import com.esprit.threadservice.service.ThreadInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/threads/{threadId}/interactions")
@RequiredArgsConstructor
public class ThreadInteractionController {

    private final ThreadInteractionService interactionService;

    /** GET /api/threads/{threadId}/interactions?userId=X */
    @GetMapping
    public ResponseEntity<ThreadInteractionStatus> getStatus(
            @PathVariable Long threadId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(interactionService.getStatus(threadId, userId));
    }

    /** GET /api/threads/{threadId}/interactions/status?userId=X */
    @GetMapping("/status")
    public ResponseEntity<ThreadInteractionStatus> getStatusAlias(
            @PathVariable Long threadId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(interactionService.getStatus(threadId, userId));
    }

    /**
     * POST /api/threads/{threadId}/interactions/vote?userId=X
     * Body: { "voteType": "UPVOTE" }
     */
    @PostMapping("/vote")
    public ResponseEntity<ThreadInteractionStatus> vote(
            @PathVariable Long threadId,
            @RequestParam Long userId,
            @RequestBody VoteRequest request) {
        return ResponseEntity.ok(interactionService.vote(threadId, userId, request.getVoteType()));
    }

    /**
     * POST /api/threads/{threadId}/interactions/react?userId=X
     * Body: { "reactionType": "LIKE" }
     */
    @PostMapping("/react")
    public ResponseEntity<ThreadInteractionStatus> react(
            @PathVariable Long threadId,
            @RequestParam Long userId,
            @RequestBody ReactRequest request) {
        return ResponseEntity.ok(interactionService.react(threadId, userId, request.getReactionType()));
    }

    /**
     * POST /api/threads/{threadId}/interactions/wishlist?userId=X
     */
    @PostMapping("/wishlist")
    public ResponseEntity<ThreadInteractionStatus> toggleWishlist(
            @PathVariable Long threadId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(interactionService.toggleWishlist(threadId, userId));
    }
}
