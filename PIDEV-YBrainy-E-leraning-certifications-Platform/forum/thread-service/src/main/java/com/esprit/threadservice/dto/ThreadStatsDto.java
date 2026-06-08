package com.esprit.threadservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class ThreadStatsDto {
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

    @Data
    public static class WeekBucket {
        private String weekLabel;
        private long threadsCount;
    }
}
