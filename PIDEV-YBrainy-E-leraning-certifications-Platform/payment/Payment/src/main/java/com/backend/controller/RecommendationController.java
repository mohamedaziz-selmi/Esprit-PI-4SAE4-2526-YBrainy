package com.backend.controller;

import com.backend.dto.recommendation.RecommendationSummaryResponseDTO;
import com.backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/summary")
    public ResponseEntity<RecommendationSummaryResponseDTO> getSummary(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(recommendationService.getSummary(limit));
    }
}
