package esprit.tn.breadandbutteruser.dto;

import esprit.tn.breadandbutteruser.entities.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForumProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String bio;
    private String profilePicture;
    private int xp;
    private int level;
    private String levelTitle;
    private int streakDays;
    private long xpCurrentLevelMin;
    private long xpNextLevelMin;
    private long xpProgress;
    private long xpNeeded;
    private int xpToNextLevel;
    private int xpProgressPercent;
    private int progressPercent;
    private boolean isMaxLevel;

    public static ForumProfileResponse fromUser(User user) {
        if (user == null) return null;

        int level = user.getLevel();
        long xp = user.getXp();

        // XP calculation for level progression
        long xpForCurrent = minXpForLevel(level);
        long xpForNextRaw = minXpForNextLevel(level);
        boolean isMaxLevel = xpForNextRaw < 0;
        long xpForNext = isMaxLevel ? xp : xpForNextRaw;
        long xpInLevel = xp - xpForCurrent;
        long xpNeeded = xpForNext - xpForCurrent;
        int progressPct = xpNeeded > 0 ? (int) ((xpInLevel * 100.0) / xpNeeded) : 100;
        int toNextLevel = (int) Math.max(0, xpForNext - xp);

        return ForumProfileResponse.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .bio(user.getBio())
                .profilePicture(user.getProfilePicture())
                .xp((int) xp)
                .level(level)
                .levelTitle(getLevelTitle(level))
                .streakDays(user.getStreakDays())
                .xpCurrentLevelMin(xpForCurrent)
                .xpNextLevelMin(xpForNext)
                .xpProgress(xpInLevel)
                .xpNeeded(xpNeeded)
                .xpToNextLevel(toNextLevel)
                .xpProgressPercent(progressPct)
                .progressPercent(progressPct)
                .isMaxLevel(isMaxLevel)
                .build();
    }

    private static long minXpForLevel(int level) {
        if (level <= 1) return 0;
        return (long) (100 * Math.pow(2, level - 2));
    }

    private static long minXpForNextLevel(int currentLevel) {
        if (currentLevel >= 50) return -1; // Max level
        return (long) (100 * Math.pow(2, currentLevel - 1));
    }

    private static String getLevelTitle(int level) {
        if (level < 5) return "Novice";
        if (level < 10) return "Learner";
        if (level < 15) return "Scholar";
        if (level < 20) return "Expert";
        if (level < 30) return "Master";
        if (level < 40) return "Grandmaster";
        return "Legend";
    }
}
