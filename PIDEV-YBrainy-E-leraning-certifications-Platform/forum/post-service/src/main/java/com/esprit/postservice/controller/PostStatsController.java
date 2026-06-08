package com.esprit.postservice.controller;

import com.esprit.postservice.dto.UserActivityDto;
import com.esprit.postservice.model.Post;
import com.esprit.postservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts/stats")
@RequiredArgsConstructor
public class PostStatsController {

    private final PostRepository postRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserActivityDto> getUserStats(@PathVariable Long userId) {
        List<Post> posts = postRepository.findByAuthorId(userId);

        LocalDateTime since = LocalDateTime.now().minusWeeks(12);
        LinkedHashMap<String, long[]> weekMap = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime w = LocalDateTime.now().minusWeeks(i);
            weekMap.put(weekKey(w), new long[1]);
        }
        posts.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since))
                .forEach(p -> { long[] v = weekMap.get(weekKey(p.getCreatedAt())); if (v != null) v[0]++; });

        List<UserActivityDto.WeekBucket> weekly = weekMap.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    String label = "Sem " + (parts.length > 1 ? parts[1] : e.getKey());
                    return UserActivityDto.WeekBucket.builder()
                            .weekLabel(label).postsCount(e.getValue()[0]).build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(UserActivityDto.builder()
                .postCount(posts.size())
                .weeklyActivity(weekly)
                .build());
    }

    private String weekKey(LocalDateTime dt) {
        int year = dt.getYear();
        int week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "-" + String.format("%02d", week);
    }
}
