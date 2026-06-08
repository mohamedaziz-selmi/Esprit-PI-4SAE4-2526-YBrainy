package tn.esprit.quizservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QuestionRequestDTO {
    @NotBlank
    private String questionText;
    @NotEmpty
    private List<OptionRequest> options;

    @Data
    public static class OptionRequest {
        private String optionText;
        private Boolean isCorrect;
    }
}
