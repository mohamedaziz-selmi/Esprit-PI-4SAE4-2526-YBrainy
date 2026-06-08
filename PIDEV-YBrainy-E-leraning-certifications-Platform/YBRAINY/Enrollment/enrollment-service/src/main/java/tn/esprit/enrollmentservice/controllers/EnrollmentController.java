package tn.esprit.enrollmentservice.controllers;

import tn.esprit.enrollmentservice.entities.Enrollment;
import tn.esprit.enrollmentservice.services.IEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final IEnrollmentService enrollmentService;

    record EnrollRequest(Long studentId, Long courseId) {}

    @PostMapping
    public ResponseEntity<Enrollment> enroll(@RequestBody EnrollRequest body) {
        return ResponseEntity.ok(enrollmentService.enroll(body.studentId(), body.courseId()));
    }

    @PostMapping("/with-payment")
    public ResponseEntity<Enrollment> enrollWithPayment(
            @RequestParam Long studentId,
            @RequestParam Long courseId,
            @RequestParam String paymentIntentId) {
        return ResponseEntity.ok(enrollmentService.enrollWithPayment(studentId, courseId, paymentIntentId));
    }

    @GetMapping
    public ResponseEntity<List<Enrollment>> getAllEnrollments() {
        return ResponseEntity.ok(enrollmentService.getAllEnrollments());
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Enrollment>> getStudentEnrollments(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentService.getStudentEnrollments(studentId));
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> isEnrolled(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        return ResponseEntity.ok(enrollmentService.isEnrolled(studentId, courseId));
    }

    @GetMapping("/monthly-counts")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyCounts() {
        return ResponseEntity.ok(enrollmentService.getMonthlyEnrollmentCounts());
    }

    @DeleteMapping("/course/{courseId}")
    public ResponseEntity<Void> deleteEnrollmentsByCourse(@PathVariable Long courseId) {
        enrollmentService.deleteEnrollmentsByCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/course/{courseId}/progress")
    public ResponseEntity<Map<String, Object>> getCourseProgress(
            @PathVariable Long courseId,
            @RequestParam Long studentId) {
        return ResponseEntity.ok(enrollmentService.getCourseProgress(courseId, studentId));
    }

    @PostMapping("/course/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<Map<String, Object>> markComplete(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestParam Long studentId) {
        enrollmentService.markLessonComplete(courseId, lessonId, studentId);
        return ResponseEntity.ok(enrollmentService.getCourseProgress(courseId, studentId));
    }

    @PostMapping("/course/{courseId}/lessons/{lessonId}/time")
    public ResponseEntity<Void> trackTime(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestParam Long studentId,
            @RequestParam Integer seconds) {
        enrollmentService.trackTimeSpent(courseId, lessonId, studentId, seconds);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/student/{studentId}/course/{courseId}")
    public ResponseEntity<Enrollment> getEnrollment(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(enrollmentService.getEnrollment(studentId, courseId));
    }

    @GetMapping("/course/{courseId}/list")
    public ResponseEntity<List<Enrollment>> getEnrollmentsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentsByCourse(courseId));
    }

    @GetMapping("/by-certificate/{certificateId}")
    public ResponseEntity<Enrollment> getByCertificate(@PathVariable String certificateId) {
        return ResponseEntity.ok(enrollmentService.getByCertificateId(certificateId));
    }

    @PatchMapping("/{id}/certificate")
    public ResponseEntity<Void> updateCertificateId(
            @PathVariable Long id,
            @RequestParam String certificateId) {
        enrollmentService.updateCertificateId(id, certificateId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{enrollmentId}/certificate")
    public ResponseEntity<Void> updateCertificate(
            @PathVariable Long enrollmentId,
            @RequestParam String certificateId) {
        enrollmentService.updateCertificate(enrollmentId, certificateId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/students/{studentId}/dashboard")
    public ResponseEntity<Map<String, Object>> getStudentDashboard(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentService.getStudentDashboard(studentId));
    }
}
