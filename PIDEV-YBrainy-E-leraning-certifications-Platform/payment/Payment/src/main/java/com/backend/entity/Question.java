package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String questionText;

    /** Multiple-choice options */
    @Column(length = 500)
    private String optionA;

    @Column(length = 500)
    private String optionB;

    @Column(length = 500)
    private String optionC;

    @Column(length = 500)
    private String optionD;

    /** Correct answer: A, B, C, or D */
    @Column(nullable = false)
    private String correctAnswer;

    /** Points for this question */
    private Integer points;

    /** Order within the quiz */
    private Integer orderIndex;

    /** multiple_choice, true_false, short_answer */
    private String questionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    @JsonIgnore
    private Quiz quiz;

    public Long getQuizId() {
        return quiz != null ? quiz.getId() : null;
    }

    @PrePersist
    public void prePersist() {
        if (this.points == null) this.points = 1;
        if (this.orderIndex == null) this.orderIndex = 0;
        if (this.questionType == null) this.questionType = "multiple_choice";
    }
}

