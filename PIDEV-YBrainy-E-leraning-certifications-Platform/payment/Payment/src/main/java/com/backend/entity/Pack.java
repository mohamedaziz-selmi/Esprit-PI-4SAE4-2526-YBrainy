package com.backend.entity;

import com.backend.entity.enums.PackLevel;
import com.backend.entity.enums.PackStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "packs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double originalPrice;

    @Column(nullable = false)
    private Double salePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackLevel level;

    @Column(nullable = false)
    private Integer durationHours;

    private String certificateName;

    private String image;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PackStatus status = PackStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private PackCategory category;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
