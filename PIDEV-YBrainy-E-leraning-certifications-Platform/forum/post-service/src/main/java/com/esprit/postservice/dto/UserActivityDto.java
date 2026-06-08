package com.esprit.postservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserActivityDto {
    private long postCount;
    private List<WeekBucket> weeklyActivity;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WeekBucket {
        private String weekLabel;
        private long postsCount;
    }
}
