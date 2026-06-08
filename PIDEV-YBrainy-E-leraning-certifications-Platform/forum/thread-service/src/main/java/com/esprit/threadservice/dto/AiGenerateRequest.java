package com.esprit.threadservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiGenerateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 300, message = "Title must be between 3 and 300 characters")
    private String title;
}
