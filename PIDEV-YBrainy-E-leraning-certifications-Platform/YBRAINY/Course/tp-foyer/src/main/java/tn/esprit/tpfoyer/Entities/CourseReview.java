package tn.esprit.tpfoyer.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "course_reviews", indexes = {
        @Index(name = "idx_review_course",         columnList = "course_id"),
        @Index(name = "idx_review_student_course", columnList = "student_id, course_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
