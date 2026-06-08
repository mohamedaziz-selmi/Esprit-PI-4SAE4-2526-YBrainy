package tn.esprit.lessonservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LessonMetaRequestDTO {
    @NotBlank
    private String title;
    private String description;
    private Integer orderIndex;
    private Integer durationMinutes;
}
