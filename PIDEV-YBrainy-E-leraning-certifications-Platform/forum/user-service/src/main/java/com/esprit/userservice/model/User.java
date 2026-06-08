package com.esprit.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.STUDENT;

    @Column(nullable = false)
    @Builder.Default
    private long xp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(nullable = false)
    @Builder.Default
    private int streakDays = 0;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        STUDENT, INSTRUCTOR, ADMIN
    }
}
