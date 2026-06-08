package com.esprit.userservice.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlQualityStatsDto {
    private int hqCount;
    private int lqEditCount;
    private int lqCloseCount;
    private int totalAnalyzed;
    private int totalThreads;
    private double hqRate;
    private boolean available;
}
