package com.esprit.userservice.controller;

import com.esprit.userservice.dto.UpdateProfileRequest;
import com.esprit.userservice.dto.UserProfileResponse;
import com.esprit.userservice.dto.XpEventResponse;
import com.esprit.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum-users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /** Angular calls GET /api/users/{userId}/profile */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getProfileAlias(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserProfileResponse> getByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getProfileByUsername(username));
    }

    @GetMapping
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<UserProfileResponse>> getLeaderboard() {
        return ResponseEntity.ok(userService.getLeaderboard());
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable Long userId,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /** Angular calls GET /api/users/{userId}/xp-recent */
    @GetMapping("/{userId}/xp-recent")
    public ResponseEntity<List<XpEventResponse>> getRecentXp(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getRecentXp(userId));
    }

    @GetMapping("/{userId}/xp-history")
    public ResponseEntity<List<XpEventResponse>> getXpHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getXpHistory(userId));
    }
}
