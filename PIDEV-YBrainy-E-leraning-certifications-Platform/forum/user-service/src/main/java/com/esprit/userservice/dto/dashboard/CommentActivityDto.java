package com.esprit.userservice.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class CommentActivityDto {
    private long commentCount;
    private List<WeekBucket> weeklyActivity;

    @Getter @Setter
    public static class WeekBucket {
        private String weekLabel;
        private long commentsCount;
    }
}
