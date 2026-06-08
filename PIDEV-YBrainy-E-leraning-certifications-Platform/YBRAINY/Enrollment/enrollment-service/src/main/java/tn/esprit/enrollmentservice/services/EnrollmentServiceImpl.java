package tn.esprit.enrollmentservice.services;

import tn.esprit.enrollmentservice.clients.CourseClient;
import tn.esprit.enrollmentservice.clients.LessonClient;
import tn.esprit.enrollmentservice.entities.Enrollment;
import tn.esprit.enrollmentservice.entities.EnrollmentStatus;
import tn.esprit.enrollmentservice.events.EnrollmentCompletedEvent;
import tn.esprit.enrollmentservice.repositories.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentServiceImpl implements IEnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LessonClient lessonClient;
    private final CourseClient courseClient;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        // Validate course exists via Course Service
        try {
            boolean exists = courseClient.courseExists(courseId);
            if (!exists) {
                throw new RuntimeException("Course not found: " + courseId);
            }
        } catch (feign.FeignException e) {
            log.warn("[Enrollment] Could not validate course {}: {}",
                courseId, e.getMessage());
            // Fail-closed: reject if course service is unreachable
            throw new RuntimeException("Could not verify course existence");
        }

        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId).get();
        }
        return enrollmentRepository.save(Enrollment.builder()
            .studentId(studentId)
            .courseId(courseId)
            .status(EnrollmentStatus.ACTIVE)
            .enrollmentDate(LocalDateTime.now())
            .completionPercentage(0.0)
            .build());
    }

    @Override
    @Transactional
    public Enrollment enrollWithPayment(Long studentId, Long courseId, String paymentIntentId) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId).get();
        }
        return enrollmentRepository.save(Enrollment.builder()
            .studentId(studentId)
            .courseId(courseId)
            .paymentIntentId(paymentIntentId)
            .status(EnrollmentStatus.ACTIVE)
            .enrollmentDate(LocalDateTime.now())
            .completionPercentage(0.0)
            .build());
    }

    @Override
    public List<Enrollment> getStudentEnrollments(Long studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }

    @Override
    public Enrollment getEnrollment(Long studentId, Long courseId) {
        return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
            .orElseThrow(() -> new RuntimeException(
                "Enrollment not found for studentId=" + studentId + " courseId=" + courseId));
    }

    @Override
    public boolean isEnrolled(Long studentId, Long courseId) {
        return enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId);
    }

    @Override
    @Transactional
    public void deleteEnrollmentsByCourse(Long courseId) {
        enrollmentRepository.deleteAllByCourseId(courseId);
    }

    @Override
    public List<Map<String, Object>> getMonthlyEnrollmentCounts() {
        List<Object[]> rows = enrollmentRepository.countByMonth();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("month", row[0]);
            map.put("year", row[1]);
            map.put("count", row[2]);
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional
    public Enrollment markLessonComplete(Long courseId, Long lessonId, Long studentId) {
        Enrollment enrollment = getEnrollment(studentId, courseId);

        int totalLessons = 0;
        long completedCount = 0;
        try {
            lessonClient.trackProgress(enrollment.getId(), lessonId, "COMPLETED", null);
            // Count only AFTER progress is confirmed saved — avoids stale count race
            Long completed = lessonClient.getCompletedLessonCount(enrollment.getId());
            if (completed != null) completedCount = completed;
        } catch (Exception e) {
            log.error("[Enrollment] trackProgress failed, aborting lesson completion: {}", e.getMessage());
            throw new RuntimeException("Failed to record lesson progress: " + e.getMessage(), e);
        }

        try {
            List<Map<String, Object>> allLessons = lessonClient.getLessons(courseId);
            if (allLessons != null && !allLessons.isEmpty()) {
                allLessons.sort((a, b) -> {
                    int oa = a.get("orderIndex") != null ?
                        ((Number)a.get("orderIndex")).intValue() : 0;
                    int ob = b.get("orderIndex") != null ?
                        ((Number)b.get("orderIndex")).intValue() : 0;
                    return Integer.compare(oa, ob);
                });
                int currentIdx = -1;
                for (int i = 0; i < allLessons.size(); i++) {
                    Object lid = allLessons.get(i).get("id");
                    if (lid != null && ((Number)lid).longValue() == lessonId) {
                        currentIdx = i;
                        break;
                    }
                }
                if (currentIdx >= 0 && currentIdx + 1 < allLessons.size()) {
                    Object nextId = allLessons.get(currentIdx + 1).get("id");
                    enrollment.setCurrentLessonId(((Number)nextId).longValue());
                } else {
                    enrollment.setCurrentLessonId(lessonId);
                }
            } else {
                enrollment.setCurrentLessonId(lessonId);
            }
        } catch (Exception e) {
            log.warn("[Enrollment] Could not advance currentLessonId: {}", e.getMessage());
            enrollment.setCurrentLessonId(lessonId);
        }

        try {
            Long total = lessonClient.getLessonCount(courseId);
            if (total != null) totalLessons = total.intValue();
        } catch (Exception ignored) {}

        double percentage = totalLessons > 0
            ? (completedCount / (double) totalLessons) * 100.0 : 0.0;
        enrollment.setCompletionPercentage(percentage);

        if (percentage >= 100.0) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(LocalDateTime.now());
        }

        Enrollment saved = enrollmentRepository.save(enrollment);

        if (percentage >= 100.0) {
            // publish enrollment.completed event → Course Service issues certificate
            try {
                EnrollmentCompletedEvent event = EnrollmentCompletedEvent.builder()
                    .enrollmentId(saved.getId())
                    .studentId(saved.getStudentId())
                    .courseId(saved.getCourseId())
                    .build();
                rabbitTemplate.convertAndSend(
                    "enrollment.exchange",
                    "enrollment.completed",
                    event);
                log.info("[Enrollment] Published enrollment.completed for studentId={} courseId={}",
                    saved.getStudentId(), saved.getCourseId());
            } catch (Exception e) {
                log.warn("[Enrollment] Could not publish enrollment.completed: {}", e.getMessage());
            }
        }

        return saved;
    }

    @Override
    public void trackTimeSpent(Long courseId, Long lessonId, Long studentId, Integer seconds) {
        enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
            .ifPresent(enrollment -> {
                try {
                    lessonClient.trackTimeOnly(enrollment.getId(), lessonId,
                        seconds.longValue());
                } catch (Exception e) {
                    log.warn("[Enrollment] trackTimeOnly failed: {}", e.getMessage());
                }
            });
    }

    @Override
    public List<Enrollment> getEnrollmentsByCourse(Long courseId) {
        return enrollmentRepository.findByCourseId(courseId);
    }

    @Override
    public List<Enrollment> getAllEnrollments() {
        return enrollmentRepository.findAll();
    }

    @Override
    public Enrollment getByCertificateId(String certificateId) {
        return enrollmentRepository.findByCertificateId(certificateId)
            .orElseThrow(() -> new RuntimeException("Certificate not found: " + certificateId));
    }

    @Override
    @Transactional
    public void updateCertificateId(Long enrollmentId, String certificateId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollmentId));
        enrollment.setCertificateId(certificateId);
        enrollmentRepository.save(enrollment);
    }

    @Override
    @Transactional
    public void updateCertificate(Long enrollmentId, String certificateId) {
        Enrollment e = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollmentId));
        e.setCertificateId(certificateId);
        enrollmentRepository.save(e);
        log.info("[Enrollment] Certificate {} written to enrollment {}", certificateId, enrollmentId);
    }

    @Override
    public Map<String, Object> getStudentDashboard(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);

        List<Map<String, Object>> enrolledCourses = new ArrayList<>();
        double progressSum = 0.0;
        int totalCompleted = 0;
        int totalInProgress = 0;

        for (Enrollment enrollment : enrollments) {
            Map<String, Object> courseData = new LinkedHashMap<>();

            // Get course details via Feign
            Map<String, Object> course = null;
            try {
                course = courseClient.getCourse(enrollment.getCourseId());
            } catch (Exception e) {
                log.warn("[Dashboard] Could not fetch course {}: {}",
                    enrollment.getCourseId(), e.getMessage());
            }

            // Get lesson counts via Feign
            int totalLessons = 0;
            int completedLessons = 0;
            try {
                Long total = lessonClient.getLessonCount(enrollment.getCourseId());
                if (total != null) totalLessons = total.intValue();
            } catch (Exception ignored) {}
            try {
                Long completed = lessonClient.getCompletedLessonCount(enrollment.getId());
                if (completed != null) completedLessons = completed.intValue();
            } catch (Exception ignored) {}

            courseData.put("courseId", enrollment.getCourseId());
            courseData.put("courseTitle", course != null ? course.get("title") : "Unknown");
            courseData.put("thumbnailUrl", course != null ? course.get("thumbnailUrl") : null);
            courseData.put("category", course != null ? course.get("category") : null);
            courseData.put("level", course != null ? course.get("level") : null);
            courseData.put("price", course != null ? course.get("price") : 0);
            courseData.put("completionPercentage",
                enrollment.getCompletionPercentage() != null
                    ? enrollment.getCompletionPercentage() : 0.0);
            courseData.put("enrollmentStatus", enrollment.getStatus().name());
            courseData.put("enrollmentDate", enrollment.getEnrollmentDate());
            courseData.put("completedAt", enrollment.getCompletedAt());
            courseData.put("certificateId", enrollment.getCertificateId());
            courseData.put("totalLessons", totalLessons);
            courseData.put("completedLessons", completedLessons);
            courseData.put("paymentIntentId", enrollment.getPaymentIntentId());

            enrolledCourses.add(courseData);

            double pct = enrollment.getCompletionPercentage() != null
                ? enrollment.getCompletionPercentage() : 0.0;
            progressSum += pct;

            if (EnrollmentStatus.COMPLETED.equals(enrollment.getStatus())) {
                totalCompleted++;
            } else {
                totalInProgress++;
            }
        }

        double avgProgress = enrollments.isEmpty() ? 0.0 : progressSum / enrollments.size();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("studentId", studentId);
        dashboard.put("enrolledCourses", enrolledCourses);
        dashboard.put("totalEnrolled", enrollments.size());
        dashboard.put("totalCompleted", totalCompleted);
        long totalCertificates = enrollments.stream()
            .filter(e -> e.getCertificateId() != null)
            .count();
        dashboard.put("totalCertificates", totalCertificates);
        dashboard.put("totalInProgress", totalInProgress);
        dashboard.put("averageProgress", Math.round(avgProgress * 10.0) / 10.0);
        return dashboard;
    }

    @Override
    public Map<String, Object> getCourseProgress(Long courseId, Long studentId) {
        Enrollment enrollment = getEnrollment(studentId, courseId);

        List<Map<String, Object>> lessonProgresses = Collections.emptyList();
        try {
            lessonProgresses = lessonClient.getProgressByEnrollment(enrollment.getId());
        } catch (Exception ignored) {}

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("enrollmentId", enrollment.getId());
        result.put("completionPercentage", enrollment.getCompletionPercentage());
        result.put("enrollmentStatus", enrollment.getStatus().name());
        result.put("currentLessonId", enrollment.getCurrentLessonId());
        result.put("lessonProgresses", lessonProgresses);
        return result;
    }
}
