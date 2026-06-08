package com.esprit.threadservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "thread_votes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"thread_id", "voter_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private ForumThread thread;

    @Column(nullable = false)
    private Long voterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteType voteType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
