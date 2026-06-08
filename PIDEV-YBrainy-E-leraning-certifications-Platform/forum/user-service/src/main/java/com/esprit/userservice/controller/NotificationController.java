package com.esprit.userservice.controller;

import com.esprit.userservice.dto.NotificationResponse;
import com.esprit.userservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getForUser(userId));
    }

    /** Angular calls GET /api/notifications/count?userId=X → returns plain number */
    @GetMapping("/count")
    public ResponseEntity<Long> getCount(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.countUnread(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@RequestParam Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    /** Angular calls PATCH /api/notifications/{id}/read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    /** Angular calls PATCH /api/notifications/read-all?userId=X */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllReadPatch(@RequestParam Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(@RequestParam Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}
