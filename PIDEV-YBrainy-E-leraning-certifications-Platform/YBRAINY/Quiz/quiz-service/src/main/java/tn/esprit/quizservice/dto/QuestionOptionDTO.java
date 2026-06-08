package tn.esprit.quizservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionOptionDTO {
    private Long id;
    private Long questionId;
    private String optionText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
