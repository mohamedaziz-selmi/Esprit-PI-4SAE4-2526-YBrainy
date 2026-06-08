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
public class XpEventResponse {
    private Long id;
    private String sourceType;
    private int amount;
    private long newTotal;
    private int newLevel;
    private String description;
    private boolean levelUp;
    private LocalDateTime createdAt;
}
