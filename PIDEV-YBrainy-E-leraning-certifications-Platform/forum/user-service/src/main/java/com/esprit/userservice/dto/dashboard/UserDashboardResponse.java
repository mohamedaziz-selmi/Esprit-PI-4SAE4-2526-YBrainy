package com.esprit.userservice.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserDashboardResponse {
    private long totalThreadsCreated;
    private long totalPostsCreated;
    private long totalCommentsCreated;
    private long totalUpvotesReceived;
    private long totalDownvotesReceived;
    private long totalPositiveReactions;
    private long totalNegativeReactions;
    private double positiveToNegativeRatio;
    private long totalSavesReceived;
    private double avgUpvotesPerPost;
    private double avgDownvotesPerPost;
    private double avgReactionsPerPost;
    // Flat fields (kept for backward compat)
    private String bestThreadTitle;
    private Long bestThreadId;
    private long bestThreadUpvotes;
    // Nested object — matches Angular's PostSummary { id, title, upvotes, totalReactions }
    private BestPost bestPerformingPost;
    private double engagementRate;
    private Map<String, Long> reactionStats;
    private List<XpDataPointDto> xpTimeline;
    private List<DayActivityDto> weeklyActivity;
    private double communityAvgUpvotesPerPost;
    private double communityAvgReactionsPerPost;
    private double communityAvgEngagementRate;
    private int userPercentile;
    private int rankPosition;
    private List<PerformanceInsightDto> performanceInsights;

    // ── ML quality analysis ───────────────────────────────────────────────────
    private double mlHqRate;
    private int mlHqCount;
    private int mlLqEditCount;
    private int mlLqCloseCount;
    private int mlTotalAnalyzed;
    private boolean mlAvailable;

    // ── Prediction ────────────────────────────────────────────────────────────
    private int predictedNextWeekPosts;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private List<LeaderboardEntryDto> topLeaderboard;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BestPost {
        private Long id;
        private String title;
        private long upvotes;
        private long totalReactions;
    }
}
