package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.ThreadUserStatsDto;
import com.esprit.threadservice.model.ForumThread;
import com.esprit.threadservice.model.ReactionType;
import com.esprit.threadservice.model.VoteType;
import com.esprit.threadservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThreadStatsService {

    private final ThreadRepository threadRepository;
    private final ThreadVoteRepository voteRepository;
    private final ThreadReactionRepository reactionRepository;
    private final ThreadWishlistRepository wishlistRepository;

    public ThreadUserStatsDto getStatsForUser(Long userId) {
        List<ForumThread> userThreads = threadRepository.findByAuthorIdOrderByCreatedAtDesc(userId);

        // Aggregate via targeted DB queries — no full-table scans
        long upvotes   = voteRepository.countByAuthorIdAndVoteType(userId, VoteType.UPVOTE);
        long downvotes = voteRepository.countByAuthorIdAndVoteType(userId, VoteType.DOWNVOTE);
        long likes     = reactionRepository.countByAuthorIdAndReactionType(userId, ReactionType.LIKE);
        long dislikes  = reactionRepository.countByAuthorIdAndReactionType(userId, ReactionType.DISLIKE);
        // savesReceived = how many other users saved this user's threads
        long saves     = wishlistRepository.countSavesReceivedByAuthor(userId);

        // Best thread: highest upvotes among this user's threads
        Long bestId = null;
        String bestTitle = null;
        long bestUpvotes = 0, bestTotalReactions = 0;
        for (ForumThread t : userThreads) {
            long u  = voteRepository.countByThreadIdAndVoteType(t.getId(), VoteType.UPVOTE);
            long d  = voteRepository.countByThreadIdAndVoteType(t.getId(), VoteType.DOWNVOTE);
            long l  = reactionRepository.countByThreadIdAndReactionType(t.getId(), ReactionType.LIKE);
            long di = reactionRepository.countByThreadIdAndReactionType(t.getId(), ReactionType.DISLIKE);
            if (bestId == null || u > bestUpvotes) {
                bestUpvotes        = u;
                bestTotalReactions = u + d + l + di;
                bestId             = t.getId();
                bestTitle          = t.getTitle();
            }
        }

        // Community totals — single COUNT query per type, no full-table load
        long commUpvotes   = voteRepository.countByVoteType(VoteType.UPVOTE);
        long commDownvotes = voteRepository.countByVoteType(VoteType.DOWNVOTE);
        long commLikes     = reactionRepository.countByReactionType(ReactionType.LIKE);
        long commDislikes  = reactionRepository.countByReactionType(ReactionType.DISLIKE);

        return ThreadUserStatsDto.builder()
                .threadCount(userThreads.size())
                .upvotesReceived(upvotes)
                .downvotesReceived(downvotes)
                .likesReceived(likes)
                .dislikesReceived(dislikes)
                .savesReceived(saves)
                .communityTotalThreads(threadRepository.count())
                .communityTotalUpvotes(commUpvotes)
                .communityTotalDownvotes(commDownvotes)
                .communityTotalLikes(commLikes)
                .communityTotalDislikes(commDislikes)
                .bestThreadId(bestId)
                .bestThreadTitle(bestTitle)
                .bestThreadUpvotes(bestUpvotes)
                .bestThreadTotalReactions(bestTotalReactions)
                .weeklyActivity(buildWeeklyActivity(userThreads))
                .build();
    }

    private List<ThreadUserStatsDto.WeekBucket> buildWeeklyActivity(List<ForumThread> threads) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(12);
        LinkedHashMap<String, long[]> weekMap = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime w = LocalDateTime.now().minusWeeks(i);
            weekMap.put(weekKey(w), new long[1]);
        }
        threads.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(since))
                .forEach(t -> {
                    long[] v = weekMap.get(weekKey(t.getCreatedAt()));
                    if (v != null) v[0]++;
                });
        return weekMap.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    String label = "Sem " + (parts.length > 1 ? parts[1] : e.getKey());
                    return ThreadUserStatsDto.WeekBucket.builder()
                            .weekLabel(label).threadsCount(e.getValue()[0]).build();
                })
                .collect(Collectors.toList());
    }

    private String weekKey(LocalDateTime dt) {
        int year = dt.getYear();
        int week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "-" + String.format("%02d", week);
    }
}
