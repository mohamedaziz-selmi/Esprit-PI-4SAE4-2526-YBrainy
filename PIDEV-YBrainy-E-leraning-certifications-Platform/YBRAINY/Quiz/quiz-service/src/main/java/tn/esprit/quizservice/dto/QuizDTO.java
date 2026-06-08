package tn.esprit.quizservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuizDTO {
    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxAttempts;
    private LocalDateTime createdAt;
    private Integer questionCount;
}
