package tn.esprit.enrollmentservice.services;

import tn.esprit.enrollmentservice.entities.Enrollment;
import java.util.List;
import java.util.Map;

public interface IEnrollmentService {
    Enrollment enroll(Long studentId, Long courseId);
    Enrollment enrollWithPayment(Long studentId, Long courseId, String paymentIntentId);
    List<Enrollment> getStudentEnrollments(Long studentId);
    Enrollment getEnrollment(Long studentId, Long courseId);
    boolean isEnrolled(Long studentId, Long courseId);
    void deleteEnrollmentsByCourse(Long courseId);
    List<Map<String, Object>> getMonthlyEnrollmentCounts();
    Enrollment markLessonComplete(Long courseId, Long lessonId, Long studentId);
    void trackTimeSpent(Long courseId, Long lessonId, Long studentId, Integer seconds);
    Map<String, Object> getCourseProgress(Long courseId, Long studentId);
    List<Enrollment> getEnrollmentsByCourse(Long courseId);
    List<Enrollment> getAllEnrollments();
    Enrollment getByCertificateId(String certificateId);
    void updateCertificateId(Long enrollmentId, String certificateId);
    void updateCertificate(Long enrollmentId, String certificateId);
    Map<String, Object> getStudentDashboard(Long studentId);
}
