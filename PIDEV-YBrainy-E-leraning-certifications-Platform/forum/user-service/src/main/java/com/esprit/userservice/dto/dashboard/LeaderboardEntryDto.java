package com.esprit.userservice.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LeaderboardEntryDto {
    private Long userId;
    private String username;
    private int level;
    private String levelTitle;
    private int xp;
    private long threadCount;
    private double hqRate;
    private int mlTotalAnalyzed;
    private int rankPosition;
}
