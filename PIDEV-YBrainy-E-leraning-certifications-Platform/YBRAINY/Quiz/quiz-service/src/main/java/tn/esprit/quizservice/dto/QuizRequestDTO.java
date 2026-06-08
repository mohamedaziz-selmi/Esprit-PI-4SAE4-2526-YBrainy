package tn.esprit.quizservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuizRequestDTO {
    @NotBlank
    private String title;
    private String description;
    private Integer timeLimitMinutes;
    @Min(1) @Max(100)
    private Integer passingScore;
    @Min(1)
    private Integer maxAttempts;
}
