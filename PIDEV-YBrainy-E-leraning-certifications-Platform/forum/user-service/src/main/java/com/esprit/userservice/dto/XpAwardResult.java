package com.esprit.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class XpAwardResult {
    private Long userId;
    private int xpAwarded;
    private int newXpTotal;
    private int newLevel;
    private boolean leveledUp;
}
