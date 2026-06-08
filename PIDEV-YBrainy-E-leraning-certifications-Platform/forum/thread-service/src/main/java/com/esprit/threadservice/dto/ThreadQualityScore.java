package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadQualityScore {
    private int clarity;
    private int detail;
    private int usefulness;
    private int overall;
    private String label;
    private String labelColor;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private String improvedVersion;
}
