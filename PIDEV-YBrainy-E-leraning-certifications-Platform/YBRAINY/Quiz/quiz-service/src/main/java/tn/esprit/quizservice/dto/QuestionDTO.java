package tn.esprit.quizservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuestionDTO {
    private Long id;
    private Long quizId;
    private String questionText;
    private Integer orderIndex;
    private LocalDateTime createdAt;
    private List<QuestionOptionDTO> options;
}
