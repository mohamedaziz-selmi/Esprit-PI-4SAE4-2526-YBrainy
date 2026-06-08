package com.esprit.userservice.service;

import com.esprit.userservice.dto.dashboard.*;
import com.esprit.userservice.feign.CommentStatsFeignClient;
import com.esprit.userservice.feign.PostStatsFeignClient;
import com.esprit.userservice.feign.ThreadMlFeignClient;
import com.esprit.userservice.feign.ThreadStatsFeignClient;
import com.esprit.userservice.model.LevelConfig;
import com.esprit.userservice.model.User;
import com.esprit.userservice.repository.UserRepository;
import com.esprit.userservice.repository.UserXpEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserRepository userRepository;
    private final UserXpEventRepository xpEventRepository;
    private final UserService userService;
    private final ThreadStatsFeignClient threadFeign;
    private final PostStatsFeignClient postFeign;
    private final CommentStatsFeignClient commentFeign;
    private final ThreadMlFeignClient threadMlFeign;

    public UserDashboardResponse getDashboard(Long userId) {
        // Auto-create a placeholder record if this user hasn't engaged with the forum yet
        User user = userService.findOrCreatePlaceholderUser(userId);

        // Fetch stats from each service (graceful fallback on failure)
        ThreadUserStatsDto threadStats = safeCall(() -> threadFeign.getUserStats(userId), new ThreadUserStatsDto());
        PostActivityDto postStats = safeCall(() -> postFeign.getUserStats(userId), new PostActivityDto());
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

        double posNegRatio = totalNegative == 0 ? totalPositive : round2((double) totalPositive / totalNegative);
        double avgUpvotes     = totalThreads == 0 ? 0 : round2((double) upvotes / totalThreads);
        double avgDownvotes   = totalThreads == 0 ? 0 : round2((double) downvotes / totalThreads);
        double avgReactions   = totalThreads == 0 ? 0 : round2((double) totalReactions / totalThreads);
        double engagementRate = totalThreads == 0 ? 0 : round2((double) totalReactions / totalThreads * 100);

        // Community averages
        long commThreads  = threadStats.getCommunityTotalThreads();
        long commUpvotes  = threadStats.getCommunityTotalUpvotes();
        long commDownvotes = threadStats.getCommunityTotalDownvotes();
        long commLikes    = threadStats.getCommunityTotalLikes();
        long commDislikes = threadStats.getCommunityTotalDislikes();
        long commReactions = commUpvotes + commDownvotes + commLikes + commDislikes;

        double commAvgUpvotes    = commThreads == 0 ? 0 : round2((double) commUpvotes / commThreads);
        double commAvgReactions  = commThreads == 0 ? 0 : round2((double) commReactions / commThreads);
        double commAvgEngagement = commThreads == 0 ? 0 : round2((double) commReactions / commThreads * 100);

        // Rank
        List<Long> rankedIds = userRepository.findAllByOrderByXpDesc()
                .stream().map(User::getId).collect(Collectors.toList());
        int rankPosition = rankedIds.indexOf(userId) + 1;
        long totalUsers  = rankedIds.size();
        int percentile   = totalUsers == 0 ? 0 : (int) Math.round((double) rankPosition / totalUsers * 100);

        // XP timeline
        List<XpDataPointDto> xpTimeline = xpEventRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(30)
                .sorted(Comparator.comparing(com.esprit.userservice.model.UserXpEvent::getCreatedAt))
                .map(e -> XpDataPointDto.builder()
                        .date(e.getCreatedAt().toLocalDate())
                        .xpGained(e.getAmount())
                        .newTotal((int) e.getNewTotal())
                        .source(e.getSourceType().name())
                        .build())
                .collect(Collectors.toList());

        // Weekly activity — merge all three weekly buckets
        List<DayActivityDto> weekly = mergeWeeklyActivity(
                threadStats.getWeeklyActivity(),
                postStats.getWeeklyActivity(),
                commentStats.getWeeklyActivity());

        // Reaction stats chart
        Map<String, Long> reactionStats = new LinkedHashMap<>();
        reactionStats.put("UPVOTE",   upvotes);
        reactionStats.put("DOWNVOTE", downvotes);
        reactionStats.put("LIKE",     likes);
        reactionStats.put("DISLIKE",  dislikes);

        // Insights
        List<PerformanceInsightDto> insights = generateInsights(
                avgUpvotes, commAvgUpvotes, downvotes, totalReactions,
                totalPositive, engagementRate, commAvgEngagement, totalThreads);

        // ML quality stats for the current user
        MlQualityStatsDto mlStats = safeCall(
                () -> threadMlFeign.getMlQualityForUser(userId),
                MlQualityStatsDto.builder().available(false).build());

        // Predict next week posts (weighted average of last 4 weeks)
        int predictedNextWeek = predictNextWeekPosts(weekly);

        // Leaderboard: top 5 users by XP
        List<LeaderboardEntryDto> leaderboard = buildLeaderboard(userId, rankedIds);

        return UserDashboardResponse.builder()
                .totalThreadsCreated(totalThreads)
                .totalPostsCreated(totalPosts)
                .totalCommentsCreated(totalComments)
                .totalUpvotesReceived(upvotes)
                .totalDownvotesReceived(downvotes)
                .totalPositiveReactions(totalPositive)
                .totalNegativeReactions(totalNegative)
                .positiveToNegativeRatio(posNegRatio)
                .totalSavesReceived(saves)
                .avgUpvotesPerPost(avgUpvotes)
                .avgDownvotesPerPost(avgDownvotes)
                .avgReactionsPerPost(avgReactions)
                .bestThreadTitle(threadStats.getBestThreadTitle())
                .bestThreadId(threadStats.getBestThreadId())
                .bestThreadUpvotes(threadStats.getBestThreadUpvotes())
                .bestPerformingPost(threadStats.getBestThreadId() != null
                        ? UserDashboardResponse.BestPost.builder()
                                .id(threadStats.getBestThreadId())
                                .title(threadStats.getBestThreadTitle())
                                .upvotes(threadStats.getBestThreadUpvotes())
                                .totalReactions(threadStats.getBestThreadTotalReactions())
                                .build()
                        : null)
                .engagementRate(engagementRate)
                .reactionStats(reactionStats)
                .xpTimeline(xpTimeline)
                .weeklyActivity(weekly)
                .communityAvgUpvotesPerPost(commAvgUpvotes)
                .communityAvgReactionsPerPost(commAvgReactions)
                .communityAvgEngagementRate(commAvgEngagement)
                .userPercentile(percentile)
                .rankPosition(rankPosition)
                .performanceInsights(insights)
                .mlHqRate(mlStats.getHqRate())
                .mlHqCount(mlStats.getHqCount())
                .mlLqEditCount(mlStats.getLqEditCount())
                .mlLqCloseCount(mlStats.getLqCloseCount())
                .mlTotalAnalyzed(mlStats.getTotalAnalyzed())
                .mlAvailable(mlStats.isAvailable())
                .predictedNextWeekPosts(predictedNextWeek)
                .topLeaderboard(leaderboard)
                .build();
    }

    public List<XpDataPointDto> getXpTimeline(Long userId) {
        return xpEventRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(30)
                .sorted(Comparator.comparing(com.esprit.userservice.model.UserXpEvent::getCreatedAt))
                .map(e -> XpDataPointDto.builder()
                        .date(e.getCreatedAt().toLocalDate())
                        .xpGained(e.getAmount())
                        .newTotal((int) e.getNewTotal())
                        .source(e.getSourceType().name())
                        .build())
                .collect(Collectors.toList());
    }

    public List<DayActivityDto> getActivity(Long userId) {
        ThreadUserStatsDto t = safeCall(() -> threadFeign.getUserStats(userId), new ThreadUserStatsDto());
        PostActivityDto p    = safeCall(() -> postFeign.getUserStats(userId), new PostActivityDto());
        CommentActivityDto c = safeCall(() -> commentFeign.getUserStats(userId), new CommentActivityDto());
        return mergeWeeklyActivity(t.getWeeklyActivity(), p.getWeeklyActivity(), c.getWeeklyActivity());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<DayActivityDto> mergeWeeklyActivity(
            List<ThreadUserStatsDto.WeekBucket> threadBuckets,
            List<PostActivityDto.WeekBucket> postBuckets,
            List<CommentActivityDto.WeekBucket> commentBuckets) {

        Map<String, DayActivityDto> map = new LinkedHashMap<>();

        if (threadBuckets != null) {
            threadBuckets.forEach(b -> map.computeIfAbsent(b.getWeekLabel(),
                    k -> DayActivityDto.builder().weekLabel(k).build()).setThreadsCount(b.getThreadsCount()));
        }
        if (postBuckets != null) {
            postBuckets.forEach(b -> map.computeIfAbsent(b.getWeekLabel(),
                    k -> DayActivityDto.builder().weekLabel(k).build()).setPostsCount(b.getPostsCount()));
        }
        if (commentBuckets != null) {
            commentBuckets.forEach(b -> map.computeIfAbsent(b.getWeekLabel(),
                    k -> DayActivityDto.builder().weekLabel(k).build()).setCommentsCount(b.getCommentsCount()));
        }
        return new ArrayList<>(map.values());
    }

    private List<PerformanceInsightDto> generateInsights(
            double avgUpvotes, double commAvgUpvotes,
            long totalDownvotes, long totalReactions, long totalPositive,
            double engagementRate, double commAvgEngagement, long totalThreads) {

        List<PerformanceInsightDto> insights = new ArrayList<>();

        if (avgUpvotes > commAvgUpvotes * 1.2) {
            insights.add(PerformanceInsightDto.builder().type("POSITIVE").icon("⬆️")
                    .message(String.format("Tes threads reçoivent en moyenne %.1f upvotes — au-dessus de la moyenne (%.1f) !", avgUpvotes, commAvgUpvotes))
                    .build());
        }
        double downvoteRatio = totalReactions == 0 ? 0 : (double) totalDownvotes / totalReactions;
        if (downvoteRatio > 0.2) {
            insights.add(PerformanceInsightDto.builder().type("NEGATIVE").icon("⚠️")
                    .message(String.format("%.0f%% de tes réactions sont négatives — relis tes contenus avant de publier.", downvoteRatio * 100))
                    .build());
        }
        if (engagementRate < commAvgEngagement && totalThreads >= 3) {
            insights.add(PerformanceInsightDto.builder().type("SUGGESTION").icon("💡")
                    .message("Enrichis tes threads avec plus de détails pour augmenter l'engagement.")
                    .build());
        }
        double positiveRatio = totalReactions == 0 ? 0 : (double) totalPositive / totalReactions;
        if (positiveRatio > 0.85 && totalReactions > 0) {
            insights.add(PerformanceInsightDto.builder().type("POSITIVE").icon("😊")
                    .message(String.format("Excellent ratio positif — %.0f%% de tes réactions sont positives !", positiveRatio * 100))
                    .build());
        }
        if (totalThreads < 5) {
            insights.add(PerformanceInsightDto.builder().type("SUGGESTION").icon("📝")
                    .message("Participe plus activement en créant des threads pour améliorer tes statistiques.")
                    .build());
        }
        if (insights.isEmpty()) {
            insights.add(PerformanceInsightDto.builder().type("SUGGESTION").icon("🚀")
                    .message("Continue à contribuer pour débloquer des insights personnalisés !")
                    .build());
        }
        return insights;
    }

    private <T> T safeCall(java.util.function.Supplier<T> supplier, T fallback) {
        try { return supplier.get(); } catch (Exception e) {
            log.warn("Feign call failed, using fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /** Predict next week total activity (threads+posts+comments) using weighted average of last 4 weeks. */
    private int predictNextWeekPosts(List<DayActivityDto> weekly) {
        if (weekly == null || weekly.isEmpty()) return 0;
        int n = Math.min(4, weekly.size());
        List<DayActivityDto> recent = weekly.subList(weekly.size() - n, weekly.size());
        // Weighted: latest week has weight n, oldest has weight 1
        double weightedSum = 0;
        double totalWeight = 0;
        for (int i = 0; i < recent.size(); i++) {
            double w = i + 1;
            DayActivityDto d = recent.get(i);
            weightedSum += w * (d.getThreadsCount() + d.getPostsCount() + d.getCommentsCount());
            totalWeight += w;
        }
        return (int) Math.round(totalWeight == 0 ? 0 : weightedSum / totalWeight);
    }

    /** Build top-5 leaderboard from users ranked by XP. */
    private List<LeaderboardEntryDto> buildLeaderboard(Long currentUserId, List<Long> rankedIds) {
        int limit = Math.min(5, rankedIds.size());
        List<LeaderboardEntryDto> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Long uid = rankedIds.get(i);
            try {
                User u = userRepository.findById(uid).orElse(null);
                if (u == null) continue;
                // Thread count from stats feign
                long threadCount = safeCall(() -> threadFeign.getUserStats(uid).getThreadCount(), 0L);
                // ML quality
                MlQualityStatsDto ml = safeCall(
                        () -> threadMlFeign.getMlQualityForUser(uid),
                        MlQualityStatsDto.builder().available(false).build());
                result.add(LeaderboardEntryDto.builder()
                        .userId(uid)
                        .username(u.getUsername())
                        .level(u.getLevel())
                        .levelTitle(LevelConfig.getTitle(u.getLevel()))
                        .xp((int) u.getXp())
                        .threadCount(threadCount)
                        .hqRate(ml.getHqRate())
                        .mlTotalAnalyzed(ml.getTotalAnalyzed())
                        .rankPosition(i + 1)
                        .build());
            } catch (Exception e) {
                log.warn("Leaderboard entry failed for user #{}: {}", uid, e.getMessage());
            }
        }
        return result;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
