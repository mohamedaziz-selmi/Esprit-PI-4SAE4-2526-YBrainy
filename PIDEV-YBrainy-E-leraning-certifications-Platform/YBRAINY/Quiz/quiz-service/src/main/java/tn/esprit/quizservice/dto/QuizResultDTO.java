package tn.esprit.quizservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultDTO {
    private Long attemptId;
    private Double score;
    private Boolean passed;
    private Integer correctAnswers;
    private Integer totalQuestions;
    private Integer passingScore;
    private Integer attemptsUsed;
    private Integer attemptsRemaining;
    private List<QuestionReviewDTO> questionReviews;
}
