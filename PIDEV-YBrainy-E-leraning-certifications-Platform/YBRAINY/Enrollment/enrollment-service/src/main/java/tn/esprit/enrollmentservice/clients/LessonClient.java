package tn.esprit.enrollmentservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "lesson-service")
public interface LessonClient {

    @GetMapping("/api/lessons/course/{courseId}")
    List<Map<String, Object>> getLessons(@PathVariable Long courseId);

    @GetMapping("/api/lessons/course/{courseId}/count")
    Long getLessonCount(@PathVariable Long courseId);

    @PostMapping("/api/lessons/progress")
    Map<String, Object> trackProgress(
        @RequestParam Long enrollmentId,
        @RequestParam Long lessonId,
        @RequestParam String status,
        @RequestParam(required = false) Long timeSpent);

    @PostMapping("/api/lessons/progress/time")
    Map<String, Object> trackTimeOnly(
        @RequestParam Long enrollmentId,
        @RequestParam Long lessonId,
        @RequestParam Long timeSpent);

    @GetMapping("/api/lessons/progress/enrollment/{enrollmentId}")
    List<Map<String, Object>> getProgressByEnrollment(@PathVariable Long enrollmentId);

    @GetMapping("/api/lessons/progress/enrollment/{enrollmentId}/completed-count")
    Long getCompletedLessonCount(@PathVariable Long enrollmentId);
}
