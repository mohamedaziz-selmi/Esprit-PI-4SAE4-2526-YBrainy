package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AiAnalysisResponse {
    private ThreadQualityScore score;
    private boolean analysisAvailable;
}
