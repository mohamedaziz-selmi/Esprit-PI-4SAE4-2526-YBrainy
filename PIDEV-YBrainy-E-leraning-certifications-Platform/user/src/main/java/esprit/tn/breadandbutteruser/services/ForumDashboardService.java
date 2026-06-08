package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.client.*;
import esprit.tn.breadandbutteruser.dto.ForumProfileResponse;
import esprit.tn.breadandbutteruser.dto.forum.*;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.XpEvent;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.XpEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumDashboardService {

    private final UserRepository userRepository;
    private final XpEventRepository xpEventRepository;
    private final UserService userService;
    private final ThreadStatsFeignClient threadFeign;
    private final PostStatsFeignClient postFeign;
    private final CommentStatsFeignClient commentFeign;
    private final ThreadMlFeignClient threadMlFeign;

    public UserDashboardResponse getDashboard(Long userId) {
        ThreadUserStatsDto threadStats = safeCall(() -> threadFeign.getUserStats(userId), new ThreadUserStatsDto());
        PostActivityDto    postStats   = safeCall(() -> postFeign.getUserStats(userId),   new PostActivityDto());
        CommentActivityDto commentStats = safeCall(() -> commentFeign.getUserStats(userId), new CommentActivityDto());

        long totalThreads  = threadStats.getThreadCount();
        long totalPosts    = postStats.getPostCount();
        long totalComments = commentStats.getCommentCount();

        long upvotes   = threadStats.getUpvotesReceived();
        long downvotes = threadStats.getDownvotesReceived();
        long likes     = threadStats.getLikesReceived();
        long dislikes  = threadStats.getDislikesReceived();
        long saves     = threadStats.getSavesReceived();

        long totalPositive  = upvotes + likes;
        long totalNegative  = downvotes + dislikes;
        long totalReactions = totalPositive + totalNegative;

        double posNegRatio    = totalNegative == 0 ? totalPositive : round2((double) totalPositive / totalNegative);
        double avgUpvotes     = totalThreads == 0 ? 0 : round2((double) upvotes / totalThreads);
        double avgDownvotes   = totalThreads == 0 ? 0 : round2((double) downvotes / totalThreads);
        double avgReactions   = totalThreads == 0 ? 0 : round2((double) totalReactions / totalThreads);
        double engagementRate = totalThreads == 0 ? 0 : round2((double) totalReactions / totalThreads * 100);

        // Community averages (top 10 users)
        List<User> allUsers = userRepository.findAll().stream()
                .sorted(Comparator.comparingLong(User::getXp).reversed())
                .limit(10).collect(Collectors.toList());

        double commAvgUpvotes   = avgCommunityUpvotes(allUsers);
        double commAvgReactions = avgCommunityReactions(allUsers);
        double commAvgEngagement = commAvgReactions * 100;

        // Ranking
        List<User> sorted = userRepository.findAll().stream()
                .sorted(Comparator.comparingLong(User::getXp).reversed())
                .collect(Collectors.toList());
        int rank = 1;
        for (User u : sorted) { if (u.getUserId().equals(userId)) break; rank++; }
        double percentile = sorted.isEmpty() ? 100 : round2((1.0 - (double)(rank - 1) / sorted.size()) * 100);

        // Reaction stats map
        Map<String, Long> reactionStats = new LinkedHashMap<>();
        reactionStats.put("UPVOTE", upvotes);
        reactionStats.put("DOWNVOTE", downvotes);
        reactionStats.put("LIKE", likes);
        reactionStats.put("DISLIKE", dislikes);

        // XP Timeline
        List<XpEvent> recentXp = xpEventRepository.findTop30ByUserUserIdOrderByCreatedAtAsc(userId);
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        List<UserDashboardResponse.XpDataPointDto> xpTimeline = recentXp.stream()
                .map(e -> UserDashboardResponse.XpDataPointDto.builder()
                        .date(e.getCreatedAt().format(fmt))
                        .xpGained(e.getAmount())
                        .newTotal(e.getNewTotal())
                        .source(e.getSourceType() != null ? e.getSourceType().name() : "")
                        .build())
                .collect(Collectors.toList());

        // Weekly activity (merged from thread/post/comment weekly buckets)
        List<UserDashboardResponse.DayActivityDto> weeklyActivity = mergeWeeklyActivity(threadStats, postStats, commentStats);

        // Insights
        List<UserDashboardResponse.PerformanceInsightDto> insights = generateInsights(
                totalPositive, totalNegative, avgUpvotes, commAvgUpvotes,
                avgReactions, commAvgReactions, totalThreads);

        // ML stats
        MlQualityStatsDto ml = safeCall(() -> threadMlFeign.getMlQualityForUser(userId), null);
        boolean mlAvailable = ml != null && ml.getTotalAnalyzed() > 0;
        double mlHqRate     = mlAvailable ? ml.getHqRate() : 0;
        long mlHqCount      = mlAvailable ? ml.getHqCount() : 0;
        long mlLqEditCount  = mlAvailable ? ml.getLqEditCount() : 0;
        long mlLqCloseCount = mlAvailable ? ml.getLqCloseCount() : 0;
        long mlTotal        = mlAvailable ? ml.getTotalAnalyzed() : 0;

        // Prediction
        double prediction = predictNextWeekPosts(weeklyActivity);

        // Leaderboard (top 5)
        List<UserDashboardResponse.LeaderboardEntryDto> leaderboard = buildLeaderboard(sorted);

        return UserDashboardResponse.builder()
                .totalThreadsCreated(totalThreads)
                .totalPostsCreated(totalPosts)
                .totalCommentsCreated(totalComments)
                .totalUpvotesReceived(upvotes)
                .totalDownvotesReceived(downvotes)
                .totalPositiveReactions(totalPositive)
                .totalNegativeReactions(totalNegative)
                .totalSavesReceived(saves)
                .positiveToNegativeRatio(posNegRatio)
                .reactionStats(reactionStats)
                .avgUpvotesPerPost(avgUpvotes)
                .avgDownvotesPerPost(avgDownvotes)
                .avgReactionsPerPost(avgReactions)
                .engagementRate(engagementRate)
                .xpTimeline(xpTimeline)
                .weeklyActivity(weeklyActivity)
                .communityAvgUpvotesPerPost(commAvgUpvotes)
                .communityAvgReactionsPerPost(commAvgReactions)
                .communityAvgEngagementRate(commAvgEngagement)
                .userPercentile(percentile)
                .rankPosition(rank)
                .performanceInsights(insights)
                .mlHqRate(mlHqRate)
                .mlHqCount(mlHqCount)
                .mlLqEditCount(mlLqEditCount)
                .mlLqCloseCount(mlLqCloseCount)
                .mlTotalAnalyzed(mlTotal)
                .mlAvailable(mlAvailable)
                .predictedNextWeekPosts(prediction)
                .topLeaderboard(leaderboard)
                .build();
    }

    public List<UserDashboardResponse.XpDataPointDto> getXpTimeline(Long userId) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return xpEventRepository.findTop30ByUserUserIdOrderByCreatedAtAsc(userId).stream()
                .map(e -> UserDashboardResponse.XpDataPointDto.builder()
                        .date(e.getCreatedAt().format(fmt))
                        .xpGained(e.getAmount())
                        .newTotal(e.getNewTotal())
                        .source(e.getSourceType() != null ? e.getSourceType().name() : "")
                        .build())
                .collect(Collectors.toList());
    }

    public List<UserDashboardResponse.DayActivityDto> getActivity(Long userId) {
        ThreadUserStatsDto t = safeCall(() -> threadFeign.getUserStats(userId), new ThreadUserStatsDto());
        PostActivityDto    p = safeCall(() -> postFeign.getUserStats(userId),   new PostActivityDto());
        CommentActivityDto c = safeCall(() -> commentFeign.getUserStats(userId), new CommentActivityDto());
        return mergeWeeklyActivity(t, p, c);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<UserDashboardResponse.DayActivityDto> mergeWeeklyActivity(
            ThreadUserStatsDto t, PostActivityDto p, CommentActivityDto c) {

        Map<String, long[]> map = new LinkedHashMap<>();
        t.getWeeklyBuckets().forEach(b ->
                map.computeIfAbsent(b.getWeekLabel(), k -> new long[3])[0] += b.getCount());
        p.getWeeklyBuckets().forEach(b ->
                map.computeIfAbsent(b.getWeekLabel(), k -> new long[3])[1] += b.getCount());
        c.getWeeklyBuckets().forEach(b ->
                map.computeIfAbsent(b.getWeekLabel(), k -> new long[3])[2] += b.getCount());

        return map.entrySet().stream()
                .map(e -> UserDashboardResponse.DayActivityDto.builder()
                        .weekLabel(e.getKey())
                        .threadsCount(e.getValue()[0])
                        .postsCount(e.getValue()[1])
                        .commentsCount(e.getValue()[2])
                        .build())
                .collect(Collectors.toList());
    }

    private List<UserDashboardResponse.PerformanceInsightDto> generateInsights(
            long totalPositive, long totalNegative, double avgUpvotes,
            double commAvg, double avgReactions, double commAvgReactions, long totalThreads) {

        List<UserDashboardResponse.PerformanceInsightDto> list = new ArrayList<>();
        if (avgUpvotes > commAvg * 1.2)
            list.add(insight("positive", "Your threads get more upvotes than average. Keep it up!"));
        if (totalNegative > 0 && totalPositive / Math.max(1.0, totalNegative) < 0.5)
            list.add(insight("warning", "More than half your reactions are negative. Try improving content clarity."));
        if (avgReactions < commAvgReactions && totalThreads >= 3)
            list.add(insight("suggestion", "Your engagement is below average. Try adding more detail to your threads."));
        if (totalPositive > 0 && (double) totalPositive / Math.max(1, totalPositive + totalNegative) > 0.85)
            list.add(insight("positive", "Over 85% of your reactions are positive. Excellent quality!"));
        if (totalThreads < 5)
            list.add(insight("suggestion", "Participate more! Post threads and answers to earn XP."));
        if (list.isEmpty())
            list.add(insight("suggestion", "Keep engaging with the community to improve your standing."));
        return list;
    }

    private UserDashboardResponse.PerformanceInsightDto insight(String type, String text) {
        return UserDashboardResponse.PerformanceInsightDto.builder().type(type).text(text).build();
    }

    private List<UserDashboardResponse.LeaderboardEntryDto> buildLeaderboard(List<User> sorted) {
        List<UserDashboardResponse.LeaderboardEntryDto> board = new ArrayList<>();
        int pos = 1;
        for (User u : sorted) {
            if (pos > 5) break;
            board.add(UserDashboardResponse.LeaderboardEntryDto.builder()
                    .userId(u.getUserId())
                    .username(u.getUsername())
                    .level(u.getLevel())
                    .levelTitle(levelTitle(u.getLevel()))
                    .xp(u.getXp())
                    .threadCount(0)
                    .hqRate(0)
                    .mlTotalAnalyzed(0)
                    .rankPosition(pos++)
                    .build());
        }
        return board;
    }

    private double predictNextWeekPosts(List<UserDashboardResponse.DayActivityDto> weeks) {
        if (weeks.isEmpty()) return 0;
        int sz = weeks.size();
        double weightedSum = 0, totalWeight = 0;
        for (int i = 0; i < sz; i++) {
            double w = i + 1;
            weightedSum += w * (weeks.get(i).getPostsCount() + weeks.get(i).getThreadsCount());
            totalWeight += w;
        }
        return totalWeight == 0 ? 0 : round2(weightedSum / totalWeight);
    }

    private double avgCommunityUpvotes(List<User> users) { return 0; }
    private double avgCommunityReactions(List<User> users) { return 0; }

    private static String levelTitle(int level) {
        if (level < 5)  return "Newcomer";
        if (level < 10) return "Explorer";
        if (level < 15) return "Contributor";
        if (level < 20) return "Helper";
        if (level < 30) return "Expert";
        if (level < 40) return "Master";
        return "Legend";
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private <T> T safeCall(java.util.concurrent.Callable<T> fn, T fallback) {
        try { return fn.call(); } catch (Exception e) {
            log.warn("Feign call failed: {}", e.getMessage());
            return fallback;
        }
    }
}
