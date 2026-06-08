package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be 0 or greater")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal price;

    @NotNull(message = "Category is required")
    private CourseCategory category;

    @NotNull(message = "Level is required")
    private CourseLevel level;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer approximateDurationMinutes;

    private Boolean isPublished = false;

    private Boolean offersCertificate = false;

    private Long instructorId;
}