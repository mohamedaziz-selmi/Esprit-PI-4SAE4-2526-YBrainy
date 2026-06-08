package com.esprit.userservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String bio;
    private String avatarUrl;
    private int xp;
    private int level;
    private String levelTitle;
    private int streakDays;
    // XP progress fields — different naming conventions all included for compatibility
    private long xpCurrentLevelMin;
    private long xpNextLevelMin;
    private long xpProgress;     // xp earned since start of current level
    private long xpNeeded;       // xp needed to next level
    private int xpToNextLevel;   // same as xpNeeded (int version)
    private int xpProgressPercent;
    private int progressPercent;
    @JsonProperty("isMaxLevel")
    private boolean isMaxLevel;
}
