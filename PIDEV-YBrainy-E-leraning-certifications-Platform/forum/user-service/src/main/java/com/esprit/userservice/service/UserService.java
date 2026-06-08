package com.esprit.userservice.service;

import com.esprit.userservice.dto.UpdateProfileRequest;
import com.esprit.userservice.dto.UserProfileResponse;
import com.esprit.userservice.dto.XpEventResponse;
import com.esprit.userservice.exception.ResourceNotFoundException;
import com.esprit.userservice.model.LevelConfig;
import com.esprit.userservice.model.User;
import com.esprit.userservice.repository.UserRepository;
import com.esprit.userservice.repository.UserXpEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserXpEventRepository xpEventRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user = findOrCreatePlaceholderUser(userId);
        return toProfile(user);
    }

    public UserProfileResponse getProfileByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toProfile(user);
    }

    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll()
                .stream().map(this::toProfile).collect(Collectors.toList());
    }

    public List<UserProfileResponse> getLeaderboard() {
        return userRepository.findAllByOrderByXpDesc()
                .stream().map(this::toProfile).collect(Collectors.toList());
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = findOrCreatePlaceholderUser(userId);

        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            user.setUsername(req.getUsername());
        }

        userRepository.save(user);
        return toProfile(user);
    }

    public User findOrCreatePlaceholderUser(Long userId) {
        return userRepository.findById(userId).orElseGet(() -> {
            String suffix = String.valueOf(userId);
            userRepository.insertPlaceholderUser(
                    userId,
                    "user-" + suffix,
                    "user-" + suffix + "@forum.local"
            );

            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        });
    }

    public List<XpEventResponse> getRecentXp(Long userId) {
        return xpEventRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toXpResponse).collect(Collectors.toList());
    }

    public List<XpEventResponse> getXpHistory(Long userId) {
        return xpEventRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toXpResponse).collect(Collectors.toList());
    }

    private XpEventResponse toXpResponse(com.esprit.userservice.model.UserXpEvent e) {
        return XpEventResponse.builder()
                .id(e.getId())
                .sourceType(e.getSourceType() != null ? e.getSourceType().name() : null)
                .amount(e.getAmount())
                .newTotal(e.getNewTotal())
                .newLevel(e.getNewLevel())
                .levelUp(e.isLevelUp())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private UserProfileResponse toProfile(User user) {
        long xpForCurrent = LevelConfig.minXpForLevel(user.getLevel());
        long xpForNextRaw  = LevelConfig.minXpForNextLevel(user.getLevel());
        boolean isMaxLevel = xpForNextRaw < 0;
        long xpForNext    = isMaxLevel ? user.getXp() : xpForNextRaw;
        long xpInLevel    = user.getXp() - xpForCurrent;
        long xpNeeded     = xpForNext - xpForCurrent;
        int progressPct   = xpNeeded > 0 ? (int) ((xpInLevel * 100.0) / xpNeeded) : 100;
        int toNextLevel   = (int) Math.max(0, xpForNext - user.getXp());

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(String.valueOf(user.getRole()))
                .bio(null)
                .avatarUrl(null)
                .xp((int) user.getXp())
                .level(user.getLevel())
                .levelTitle(LevelConfig.getTitle(user.getLevel()))
                .streakDays(user.getStreakDays())
                .xpCurrentLevelMin(xpForCurrent)
                .xpNextLevelMin(isMaxLevel ? user.getXp() : xpForNextRaw)
                .xpProgress(xpInLevel)
                .xpNeeded(xpNeeded)
                .xpToNextLevel(toNextLevel)
                .xpProgressPercent(Math.min(100, progressPct))
                .progressPercent(Math.min(100, progressPct))
                .isMaxLevel(isMaxLevel)
                .build();
    }
}
