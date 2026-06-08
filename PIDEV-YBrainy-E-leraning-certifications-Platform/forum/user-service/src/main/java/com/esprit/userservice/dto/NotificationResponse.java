package com.esprit.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String message;
    private Long threadId;
    private boolean read;
    private LocalDateTime createdAt;
}
