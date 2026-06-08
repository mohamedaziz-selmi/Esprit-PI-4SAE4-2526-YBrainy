package com.esprit.userservice.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class ThreadUserStatsDto {
    private long threadCount;
    private long upvotesReceived;
    private long downvotesReceived;
    private long likesReceived;
    private long dislikesReceived;
    private long savesReceived;
    private long communityTotalThreads;
    private long communityTotalUpvotes;
    private long communityTotalDownvotes;
    private long communityTotalLikes;
    private long communityTotalDislikes;
    private Long bestThreadId;
    private String bestThreadTitle;
    private long bestThreadUpvotes;
    private long bestThreadTotalReactions;
    private List<WeekBucket> weeklyActivity;

    @Getter @Setter
    public static class WeekBucket {
        private String weekLabel;
        private long threadsCount;
    }
}
