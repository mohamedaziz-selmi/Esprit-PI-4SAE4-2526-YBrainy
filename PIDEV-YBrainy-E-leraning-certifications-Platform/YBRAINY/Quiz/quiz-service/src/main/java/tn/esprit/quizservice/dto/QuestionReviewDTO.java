package tn.esprit.quizservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionReviewDTO {
    private Long questionId;
    private String questionText;
    private Long selectedOptionId;
    private String selectedOptionText;
    private boolean selectedCorrect;
    private Long correctOptionId;
    private String correctOptionText;
}
