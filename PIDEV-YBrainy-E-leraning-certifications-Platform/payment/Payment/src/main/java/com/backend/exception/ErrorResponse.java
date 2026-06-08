package com.backend.exception;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> errors;
}

