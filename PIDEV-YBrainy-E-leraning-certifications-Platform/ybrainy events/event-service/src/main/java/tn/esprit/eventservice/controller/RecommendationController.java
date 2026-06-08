package tn.esprit.eventservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventservice.dto.RecommendedEventDto;
import tn.esprit.eventservice.service.PythonRecommendationService;
import tn.esprit.eventservice.service.RecommendationService;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(
        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true"
)
@AllArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final PythonRecommendationService pythonRecommendationService;

    @GetMapping("/{studentId}")
    public List<RecommendedEventDto> getRecommendationsForStudent(
            @PathVariable("studentId") long studentId,
            @RequestParam(value = "limit", defaultValue = "2") int limit) {

        return recommendationService.getRecommendationsForStudent(studentId, limit);
    }

    @GetMapping("/python/{studentId}")
    public List<RecommendedEventDto> getPythonRecommendationsForStudent(
            @PathVariable("studentId") long studentId,
            @RequestParam(value = "limit", defaultValue = "2") int limit) {

        return pythonRecommendationService.getRecommendationsForStudent(studentId, limit);
    }
}
