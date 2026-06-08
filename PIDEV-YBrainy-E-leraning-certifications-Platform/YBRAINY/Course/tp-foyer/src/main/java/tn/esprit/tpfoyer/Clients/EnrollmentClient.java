package tn.esprit.tpfoyer.Clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "enrollment-service")
public interface EnrollmentClient {

    @GetMapping("/api/enrollments")
    List<Map<String, Object>> getAllEnrollments();

    @PostMapping("/api/enrollments")
    Map<String, Object> enroll(@RequestParam Long studentId, @RequestParam Long courseId);

    @PostMapping("/api/enrollments/with-payment")
    Map<String, Object> enrollWithPayment(@RequestParam Long studentId,
                                          @RequestParam Long courseId,
                                          @RequestParam String paymentIntentId);

    @GetMapping("/api/enrollments/student/{studentId}")
    List<Map<String, Object>> getStudentEnrollments(@PathVariable Long studentId);

    @GetMapping("/api/enrollments/exists")
    Boolean isEnrolled(@RequestParam Long studentId, @RequestParam Long courseId);

    @DeleteMapping("/api/enrollments/course/{courseId}")
    void deleteEnrollmentsByCourse(@PathVariable Long courseId);

    @GetMapping("/api/enrollments/monthly-counts")
    List<Map<String, Object>> getMonthlyCounts();

    @GetMapping("/api/enrollments/course/{courseId}/progress")
    Map<String, Object> getCourseProgress(@PathVariable Long courseId, @RequestParam Long studentId);

    @PostMapping("/api/enrollments/course/{courseId}/lessons/{lessonId}/complete")
    Map<String, Object> markLessonComplete(@PathVariable Long courseId,
                                           @PathVariable Long lessonId,
                                           @RequestParam Long studentId);

    @PostMapping("/api/enrollments/course/{courseId}/lessons/{lessonId}/time")
    void trackTimeSpent(@PathVariable Long courseId,
                        @PathVariable Long lessonId,
                        @RequestParam Long studentId,
                        @RequestParam Integer seconds);

    @GetMapping("/api/enrollments/student/{studentId}/course/{courseId}")
    Map<String, Object> getEnrollment(@PathVariable Long studentId, @PathVariable Long courseId);

    @GetMapping("/api/enrollments/course/{courseId}/list")
    List<Map<String, Object>> getEnrollmentsByCourse(@PathVariable Long courseId);

    @GetMapping("/api/enrollments/by-certificate/{certificateId}")
    Map<String, Object> getByCertificate(@PathVariable String certificateId);

    @PatchMapping("/api/enrollments/{id}/certificate")
    void updateCertificateId(@PathVariable Long id, @RequestParam String certificateId);

    @PutMapping("/api/enrollments/{enrollmentId}/certificate")
    void updateCertificate(
        @PathVariable Long enrollmentId,
        @RequestParam String certificateId);

    @GetMapping("/api/enrollments/students/{studentId}/dashboard")
    Map<String, Object> getStudentDashboard(@PathVariable Long studentId);
}
