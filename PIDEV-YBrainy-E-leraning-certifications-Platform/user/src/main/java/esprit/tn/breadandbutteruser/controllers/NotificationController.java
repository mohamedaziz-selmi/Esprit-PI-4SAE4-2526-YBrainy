package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.forum.NotificationDto;
import esprit.tn.breadandbutteruser.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getAll(@RequestParam Long userId) {
        List<NotificationDto> list = notificationService.getForUser(userId)
                .stream().map(NotificationDto::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getUnreadCount(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.countUnread(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestParam Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}
