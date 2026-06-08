package tn.esprit.tpfoyer.Controllers;

import jakarta.validation.Valid;
import tn.esprit.tpfoyer.Dto.EnrollmentCreateDTO;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentClient enrollmentClient;

    // POST /api/enrollments — direct Feign call to enrollment-service
    @PostMapping("/api/enrollments")
    public ResponseEntity<?> enroll(@RequestBody @Valid EnrollmentCreateDTO body) {
        Long studentId = body.getStudentId();
        Long courseId  = body.getCourseId();
        try {
            return ResponseEntity.ok(enrollmentClient.enroll(studentId, courseId));
        } catch (feign.FeignException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Already enrolled"));
        } catch (RuntimeException e) {
            throw e;
        }
    }

    // GET /api/enrollments/student/{studentId}
    @GetMapping("/api/enrollments/student/{studentId}")
    public ResponseEntity<?> getByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentClient.getStudentEnrollments(studentId));
    }

    // GET /api/enrollments/check?studentId=&courseId=
    @GetMapping("/api/enrollments/check")
    public ResponseEntity<Map<String, Boolean>> checkEnrollment(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        boolean enrolled = Boolean.TRUE.equals(enrollmentClient.isEnrolled(studentId, courseId));
        return ResponseEntity.ok(Map.of("enrolled", enrolled));
    }

    // GET /api/courses/{courseId}/progress?studentId=
    @GetMapping("/api/courses/{courseId}/progress")
    public ResponseEntity<?> getCourseProgress(
            @PathVariable Long courseId,
            @RequestParam Long studentId) {
        return ResponseEntity.ok(enrollmentClient.getCourseProgress(courseId, studentId));
    }

    // POST /api/courses/{courseId}/lessons/{lessonId}/complete?studentId=
    @PostMapping("/api/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<?> markLessonComplete(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestParam Long studentId) {
        return ResponseEntity.ok(enrollmentClient.markLessonComplete(courseId, lessonId, studentId));
    }

    // PATCH /api/courses/{courseId}/lessons/{lessonId}/time?studentId=&seconds=
    @PatchMapping("/api/courses/{courseId}/lessons/{lessonId}/time")
    public ResponseEntity<Void> trackTime(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestParam Long studentId,
            @RequestParam Integer seconds) {
        try {
            enrollmentClient.trackTimeSpent(courseId, lessonId, studentId, seconds);
        } catch (Exception ignored) {}
        return ResponseEntity.ok().build();
    }

    // GET /api/students/{studentId}/dashboard — proxied to enrollment-service
    @GetMapping("/api/students/{studentId}/dashboard")
    public ResponseEntity<?> getStudentDashboard(@PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentClient.getStudentDashboard(studentId));
    }

    // GET /api/enrollments/monthly-counts
    @GetMapping("/api/enrollments/monthly-counts")
    public ResponseEntity<?> getMonthlyEnrollmentCounts() {
        try {
            return ResponseEntity.ok(enrollmentClient.getMonthlyCounts());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Collections.emptyList());
        }
    }
}
