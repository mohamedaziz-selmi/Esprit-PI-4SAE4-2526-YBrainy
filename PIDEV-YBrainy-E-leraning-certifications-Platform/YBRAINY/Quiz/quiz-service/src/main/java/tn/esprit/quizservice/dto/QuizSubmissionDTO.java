package tn.esprit.quizservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizSubmissionDTO {
    private Long studentId;
    private List<QuizAnswerDTO> answers;
}
