package tn.esprit.tpfoyer.Clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "quiz-service")
public interface QuizClient {

    @DeleteMapping("/api/quizzes/course/{courseId}")
    void deleteQuizzesByCourse(@PathVariable Long courseId);

    @GetMapping("/api/quizzes/best-score")
    Map<String, Object> getBestScore(
        @RequestParam Long studentId,
        @RequestParam Long courseId);

    @GetMapping("/api/quizzes/student/{studentId}/avg-score")
    Map<String, Object> getAvgScore(@PathVariable Long studentId);
}
