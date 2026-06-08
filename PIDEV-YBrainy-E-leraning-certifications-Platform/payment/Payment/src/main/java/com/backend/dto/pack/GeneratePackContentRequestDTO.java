package com.backend.dto.pack;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record GeneratePackContentRequestDTO(
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,
        String level,
        @Min(value = 1, message = "Duration must be at least 1 hour")
        Integer durationHours,
        @Size(max = 100, message = "Certificate name must not exceed 100 characters")
        String certificateName
) {
}
