package com.esprit.threadservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "threads")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ForumThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private Long authorId;

    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThreadStatus status = ThreadStatus.OPEN;

    private String mediaUrl;
    private String mediaType;

    // AI quality scoring fields (nullable — populated async after creation)
    private Integer aiOverallScore;

    @Column(length = 30)
    private String aiLabel;

    @Column(length = 20)
    private String aiLabelColor;

    @Column(length = 500)
    private String aiSummary;

    @Builder.Default
    private boolean aiAnalyzed = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
