package com.esprit.userservice.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class XpDataPointDto {
    private LocalDate date;
    private int xpGained;
    private int newTotal;
    private String source;
}
