package tn.esprit.tpfoyer.Clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "lesson-service")
public interface LessonClient {

    @GetMapping("/api/lessons/course/{courseId}")
    List<Map<String, Object>> getLessonsByCourse(@PathVariable Long courseId);

    @GetMapping("/api/lessons/search")
    List<Map<String, Object>> searchLessonsByTitle(@RequestParam String title);

    @GetMapping("/api/lessons/course/{courseId}/lesson/{lessonId}")
    Map<String, Object> getLessonByCourse(@PathVariable Long courseId, @PathVariable Long lessonId);

    @PostMapping("/api/lessons/course/{courseId}")
    Map<String, Object> createLesson(@PathVariable Long courseId, @RequestBody Map<String, Object> lesson);

    @PutMapping("/api/lessons/course/{courseId}/lesson/{lessonId}")
    Map<String, Object> updateLesson(@PathVariable Long courseId, @PathVariable Long lessonId,
                                     @RequestBody Map<String, Object> lesson);

    @GetMapping("/api/lessons/course/{courseId}/count")
    Long getLessonCount(@PathVariable Long courseId);

    @DeleteMapping("/api/lessons/course/{courseId}/lesson/{lessonId}")
    void deleteLesson(@PathVariable Long courseId,
                      @PathVariable Long lessonId);

    @GetMapping("/api/lessons/progress/enrollment/{enrollmentId}")
    List<Map<String, Object>> getProgressByEnrollment(@PathVariable Long enrollmentId);

    @PostMapping("/api/lessons/progress")
    Map<String, Object> trackProgress(@RequestParam Long enrollmentId,
                                      @RequestParam Long lessonId,
                                      @RequestParam String status,
                                      @RequestParam Long timeSpent);

    @PostMapping("/api/lessons/progress/time")
    Map<String, Object> trackTimeOnly(@RequestParam Long enrollmentId,
                                      @RequestParam Long lessonId,
                                      @RequestParam Long timeSpent);

    @PostMapping("/api/lessons/progress/by-enrollments")
    List<Map<String, Object>> getProgressByEnrollmentIds(@RequestBody List<Long> enrollmentIds);

    @GetMapping("/api/lessons/progress/active-since")
    List<Long> getActiveEnrollmentIdsSince(@RequestParam String since);

    @GetMapping("/api/lessons/progress/enrollment/{enrollmentId}/completed-count")
    Long getCompletedLessonCount(@PathVariable Long enrollmentId);

    @DeleteMapping("/api/lessons/progress/enrollments")
    void deleteProgressByEnrollments(@RequestBody List<Long> enrollmentIds);

    @DeleteMapping("/api/lessons/course/{courseId}/all")
    void deleteAllLessonsByCourse(@PathVariable Long courseId);
}
