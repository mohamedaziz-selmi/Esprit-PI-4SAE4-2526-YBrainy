package tn.esprit.enrollmentservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long courseId;

    private LocalDateTime enrollmentDate;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status;

    private Long currentLessonId;
    private Double completionPercentage;
    private String paymentIntentId;

    @Column(unique = true)
    private String certificateId;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (enrollmentDate == null) enrollmentDate = LocalDateTime.now();
        if (status == null) status = EnrollmentStatus.ACTIVE;
        if (completionPercentage == null) completionPercentage = 0.0;
    }
}
