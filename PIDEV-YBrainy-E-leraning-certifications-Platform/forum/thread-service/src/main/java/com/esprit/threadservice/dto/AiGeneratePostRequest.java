package com.esprit.threadservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiGeneratePostRequest {

    @NotBlank(message = "Thread title is required")
    @Size(min = 3, max = 300)
    private String threadTitle;

    @Size(max = 3000)
    private String threadBody;
}
