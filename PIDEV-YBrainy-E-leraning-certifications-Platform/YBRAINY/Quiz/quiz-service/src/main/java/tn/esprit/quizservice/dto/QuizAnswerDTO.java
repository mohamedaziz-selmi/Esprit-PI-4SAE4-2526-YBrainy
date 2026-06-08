package tn.esprit.quizservice.dto;

import lombok.Data;

@Data
public class QuizAnswerDTO {
    private Long questionId;
    private Long selectedOptionId;
}
