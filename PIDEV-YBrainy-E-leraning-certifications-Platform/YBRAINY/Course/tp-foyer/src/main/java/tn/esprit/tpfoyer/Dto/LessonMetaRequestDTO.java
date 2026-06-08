package tn.esprit.tpfoyer.Dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LessonMetaRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    @Min(value = 0, message = "Order index must be 0 or greater")
    private Integer orderIndex;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;
}
