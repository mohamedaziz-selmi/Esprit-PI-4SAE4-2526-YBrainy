package tn.esprit.quizservice.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_attempt_student_quiz", columnList = "student_id, quiz_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private Boolean passed;

    @Column(name = "correct_answers", nullable = false)
    private Integer correctAnswers;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @CreationTimestamp
    @Column(name = "attempted_at", updatable = false)
    private LocalDateTime attemptedAt;
}
