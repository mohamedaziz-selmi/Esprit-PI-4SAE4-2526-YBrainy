package com.backend.dto.pack;

import com.backend.entity.enums.PackLevel;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdatePackDTO {

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 200, message = "Title must be between 2 and 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Original price is required")
    @Positive(message = "Original price must be positive")
    private Double originalPrice;

    @NotNull(message = "Sale price is required")
    @PositiveOrZero(message = "Sale price must be zero or positive")
    private Double salePrice;

    @NotNull(message = "Level is required")
    private PackLevel level;

    @NotNull(message = "Duration hours is required")
    @Min(value = 1, message = "Duration must be at least 1 hour")
    private Integer durationHours;

    private String certificateName;

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}

