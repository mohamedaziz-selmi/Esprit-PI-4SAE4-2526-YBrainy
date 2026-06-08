package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadUserStatsDto {
    private long threadCount;
    private long upvotesReceived;
    private long downvotesReceived;
    private long likesReceived;
    private long dislikesReceived;
    private long savesReceived;
    // community totals for comparison
    private long communityTotalThreads;
    private long communityTotalUpvotes;
    private long communityTotalDownvotes;
    private long communityTotalLikes;
    private long communityTotalDislikes;
    // best thread
    private Long bestThreadId;
    private String bestThreadTitle;
    private long bestThreadUpvotes;
    private long bestThreadTotalReactions;
    // weekly activity (last 12 weeks)
    private List<WeekBucket> weeklyActivity;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WeekBucket {
        private String weekLabel;
        private long threadsCount;
    }
}
