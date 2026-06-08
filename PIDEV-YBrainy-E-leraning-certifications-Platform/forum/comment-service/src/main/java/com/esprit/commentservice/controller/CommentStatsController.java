package com.esprit.commentservice.controller;

import com.esprit.commentservice.model.Comment;
import com.esprit.commentservice.repository.CommentRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments/stats")
@RequiredArgsConstructor
public class CommentStatsController {

    private final CommentRepository commentRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<CommentActivityDto> getUserStats(@PathVariable Long userId) {
        List<Comment> comments = commentRepository.findByAuthorId(userId);

        LocalDateTime since = LocalDateTime.now().minusWeeks(12);
        LinkedHashMap<String, long[]> weekMap = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime w = LocalDateTime.now().minusWeeks(i);
            weekMap.put(weekKey(w), new long[1]);
        }
        comments.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since))
                .forEach(c -> { long[] v = weekMap.get(weekKey(c.getCreatedAt())); if (v != null) v[0]++; });

        List<WeekBucket> weekly = weekMap.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("-");
                    String label = "Sem " + (parts.length > 1 ? parts[1] : e.getKey());
                    return WeekBucket.builder().weekLabel(label).commentsCount(e.getValue()[0]).build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(CommentActivityDto.builder()
                .commentCount(comments.size())
                .weeklyActivity(weekly)
                .build());
    }

    private String weekKey(LocalDateTime dt) {
        int year = dt.getYear();
        int week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "-" + String.format("%02d", week);
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CommentActivityDto {
        private long commentCount;
        private List<WeekBucket> weeklyActivity;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WeekBucket {
        private String weekLabel;
        private long commentsCount;
    }
}
