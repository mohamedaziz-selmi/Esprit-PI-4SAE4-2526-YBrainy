package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.ForumProfileResponse;
import esprit.tn.breadandbutteruser.dto.XpEventDto;
import esprit.tn.breadandbutteruser.services.UserService;
import esprit.tn.breadandbutteruser.services.XpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Exposes the same forum-user contract that the Angular frontend expects
 * at /api/forum-users/**, backed by the real breadandbutteruser data.
 */
@RestController
@RequestMapping("/api/forum-users")
@RequiredArgsConstructor
public class ForumUserController {

    private final UserService userService;
    private final XpService xpService;

    @GetMapping("/{userId}")
    public ResponseEntity<ForumProfileResponse> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getForumProfile(userId));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ForumProfileResponse> getProfileAlias(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getForumProfile(userId));
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<ForumProfileResponse> getByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getForumProfileByUsername(username));
    }

    @GetMapping
    public ResponseEntity<List<ForumProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllForumProfiles());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<ForumProfileResponse>> getLeaderboard() {
        return ResponseEntity.ok(userService.getLeaderboard());
    }

    @GetMapping("/{userId}/xp-recent")
    public ResponseEntity<List<XpEventDto>> getRecentXp(@PathVariable Long userId) {
        return ResponseEntity.ok(xpService.getRecentXpEvents(userId));
    }

    @GetMapping("/{userId}/xp-history")
    public ResponseEntity<List<XpEventDto>> getXpHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(xpService.getXpHistory(userId));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ForumProfileResponse> updateProfile(
            @PathVariable Long userId,
            @RequestBody UpdateRequest req) {
        return ResponseEntity.ok(userService.updateForumProfile(userId, req.getUsername(), req.getBio()));
    }

    public static class UpdateRequest {
        private String username;
        private String bio;
        public String getUsername() { return username; }
        public String getBio() { return bio; }
    }
}
