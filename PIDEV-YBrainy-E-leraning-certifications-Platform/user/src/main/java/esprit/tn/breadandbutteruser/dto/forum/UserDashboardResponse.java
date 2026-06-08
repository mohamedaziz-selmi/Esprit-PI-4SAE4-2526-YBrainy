package esprit.tn.breadandbutteruser.dto.forum;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserDashboardResponse {

    // ── Activity metrics ──────────────────────────────────────────────────────
    private long totalThreadsCreated;
    private long totalPostsCreated;
    private long totalCommentsCreated;

    // ── Reactions ─────────────────────────────────────────────────────────────
    private long totalUpvotesReceived;
    private long totalDownvotesReceived;
    private long totalPositiveReactions;
    private long totalNegativeReactions;
    private long totalSavesReceived;
    private double positiveToNegativeRatio;
    private Map<String, Long> reactionStats;

    // ── Averages ──────────────────────────────────────────────────────────────
    private double avgUpvotesPerPost;
    private double avgDownvotesPerPost;
    private double avgReactionsPerPost;
    private double engagementRate;

    // ── XP & level ────────────────────────────────────────────────────────────
    private List<XpDataPointDto> xpTimeline;

    // ── Weekly activity ───────────────────────────────────────────────────────
    private List<DayActivityDto> weeklyActivity;

    // ── Community comparison ──────────────────────────────────────────────────
    private double communityAvgUpvotesPerPost;
    private double communityAvgReactionsPerPost;
    private double communityAvgEngagementRate;

    // ── Ranking ───────────────────────────────────────────────────────────────
    private double userPercentile;
    private int rankPosition;

    // ── Insights ──────────────────────────────────────────────────────────────
    private List<PerformanceInsightDto> performanceInsights;

    // ── ML quality ────────────────────────────────────────────────────────────
    private double mlHqRate;
    private long mlHqCount;
    private long mlLqEditCount;
    private long mlLqCloseCount;
    private long mlTotalAnalyzed;
    private boolean mlAvailable;

    // ── Prediction ────────────────────────────────────────────────────────────
    private double predictedNextWeekPosts;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private List<LeaderboardEntryDto> topLeaderboard;

    @Data @Builder
    public static class XpDataPointDto {
        private String date;
        private int xpGained;
        private long newTotal;
        private String source;
    }

    @Data @Builder
    public static class DayActivityDto {
        private String weekLabel;
        private long threadsCount;
        private long postsCount;
        private long commentsCount;
    }

    @Data @Builder
    public static class PerformanceInsightDto {
        private String type;   // "positive" | "warning" | "suggestion"
        private String text;
    }

    @Data @Builder
    public static class LeaderboardEntryDto {
        private Long userId;
        private String username;
        private int level;
        private String levelTitle;
        private long xp;
        private long threadCount;
        private double hqRate;
        private long mlTotalAnalyzed;
        private int rankPosition;
    }
}
