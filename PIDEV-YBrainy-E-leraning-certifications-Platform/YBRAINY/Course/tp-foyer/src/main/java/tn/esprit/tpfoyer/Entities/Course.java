package tn.esprit.tpfoyer.Entities;

import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "offers_certificate", nullable = false)
    @Builder.Default
    private Boolean offersCertificate = false;

    @Column(name = "certificate_path")
    private String certificatePath;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseLevel level;

    @Column(name = "approximate_duration_minutes")
    private Integer approximateDurationMinutes;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private Long instructorId;

}