package com.esprit.messagingservice.controller;

import com.esprit.messagingservice.dto.*;
import com.esprit.messagingservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** POST /api/messages — send a message */
    @PostMapping
    public ResponseEntity<MessageResponse> send(@RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.send(request));
    }

    /** GET /api/messages/conversation/{userId1}/{userId2} */
    @GetMapping("/conversation/{userId1}/{userId2}")
    public ResponseEntity<List<MessageResponse>> getConversation(
            @PathVariable Long userId1,
            @PathVariable Long userId2) {
        return ResponseEntity.ok(messageService.getConversation(userId1, userId2));
    }

    /** GET /api/messages/inbox/{userId} → ConversationPreview[] */
    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<ConversationPreviewResponse>> getInbox(@PathVariable Long userId) {
        return ResponseEntity.ok(messageService.getInbox(userId));
    }

    /** GET /api/messages/unread/{userId} → { count } */
    @GetMapping("/unread/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("count", messageService.getUnreadCount(userId)));
    }

    /** PUT /api/messages/read/{messageId} */
    @PutMapping("/read/{messageId}")
    public ResponseEntity<MessageResponse> markAsRead(@PathVariable Long messageId) {
        return ResponseEntity.ok(messageService.markAsRead(messageId));
    }

    /** PUT /api/messages/conversation/read/{senderId}/{receiverId} */
    @PutMapping("/conversation/read/{senderId}/{receiverId}")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable Long senderId,
            @PathVariable Long receiverId) {
        messageService.markConversationRead(senderId, receiverId);
        return ResponseEntity.ok().build();
    }

    /** GET /api/messages/users — all users (for new-message modal) */
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers() {
        return ResponseEntity.ok(messageService.getAllUsers());
    }
}
