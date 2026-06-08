package com.esprit.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_xp_events")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserXpEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private XpSource sourceType;

    @Column(nullable = false)
    private int amount;

    private Long referenceId;

    @Column(nullable = false)
    private long newTotal;

    @Column(nullable = false)
    private int newLevel;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isLevelUp() {
        int levelBefore = LevelConfig.computeLevel(newTotal - amount);
        return newLevel > levelBefore;
    }
}
