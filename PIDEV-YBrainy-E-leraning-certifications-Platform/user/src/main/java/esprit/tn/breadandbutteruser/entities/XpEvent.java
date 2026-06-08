package esprit.tn.breadandbutteruser.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "xp_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private XpSourceType sourceType;

    @Column(nullable = false)
    private int amount;

    @Column(name = "new_total", nullable = false)
    private long newTotal;

    @Column(name = "new_level", nullable = false)
    private int newLevel;

    @Column(name = "level_up")
    private boolean levelUp;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum XpSourceType {
        // Forum-specific sources (matched by forum event consumers)
        THREAD_CREATED,
        POST_CREATED,
        POST_DETAILED,
        COMMENT_CREATED,
        UPVOTE_RECEIVED,
        BEST_ANSWER,
        // Legacy / platform-wide sources
        FORUM_POST,
        FORUM_COMMENT,
        COURSE_COMPLETED,
        QUIZ_PASSED,
        DAILY_LOGIN,
        STREAK_BONUS,
        PROFILE_UPDATE,
        AI_APPLICATION,
        OTHER
    }
}
