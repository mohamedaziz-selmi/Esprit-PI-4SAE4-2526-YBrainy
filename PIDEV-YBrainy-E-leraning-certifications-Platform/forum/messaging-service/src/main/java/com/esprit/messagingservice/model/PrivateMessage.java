package com.esprit.messagingservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "private_messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PrivateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private Long receiverId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String mediaUrl;
    private String mediaType;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
