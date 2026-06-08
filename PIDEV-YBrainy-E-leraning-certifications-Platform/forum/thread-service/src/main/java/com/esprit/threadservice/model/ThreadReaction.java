package com.esprit.threadservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "thread_reactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"thread_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ForumThread thread;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReactionType reactionType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
