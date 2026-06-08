package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.ForumProfileResponse;
import esprit.tn.breadandbutteruser.dto.XpEventDto;
import esprit.tn.breadandbutteruser.entities.XpEvent;
import esprit.tn.breadandbutteruser.services.UserService;
import esprit.tn.breadandbutteruser.services.XpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class XpController {

    private final XpService xpService;
    private final UserService userService;

    // Forum-compatible endpoints
    @GetMapping("/{userId}/profile")
    public ResponseEntity<ForumProfileResponse> getForumProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getForumProfile(userId));
    }

    @GetMapping("/forum/{userId}")
    public ResponseEntity<ForumProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getForumProfile(userId));
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<ForumProfileResponse> getProfileByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getForumProfileByUsername(username));
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

    // Internal endpoints for other services to award XP
    @PostMapping("/{userId}/xp")
    public ResponseEntity<XpEventDto> awardXp(
            @PathVariable Long userId,
            @RequestParam int amount,
            @RequestParam XpEvent.XpSourceType sourceType,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(xpService.awardXp(userId, amount, sourceType, description));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ForumProfileResponse> updateForumProfile(
            @PathVariable Long userId,
            @RequestBody UpdateForumProfileRequest request) {
        return ResponseEntity.ok(userService.updateForumProfile(userId, request));
    }

    // DTO for profile updates
    public static class UpdateForumProfileRequest {
        private String username;
        private String bio;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
    }
}
