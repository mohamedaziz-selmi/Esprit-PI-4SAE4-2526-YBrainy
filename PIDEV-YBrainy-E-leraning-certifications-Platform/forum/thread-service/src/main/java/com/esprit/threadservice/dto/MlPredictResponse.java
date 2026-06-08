package com.esprit.threadservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPredictResponse {
    private String label;
    private Double confidence;
    private String color;
    private String message;
    private Map<String, Double> probabilities;
    /** true when Flask service is reachable, false on error */
    private boolean available;
}
