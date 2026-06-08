package tn.esprit.enrollmentservice.services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tn.esprit.enrollmentservice.entities.*;
import tn.esprit.enrollmentservice.repositories.*;
import tn.esprit.enrollmentservice.clients.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock EnrollmentRepository enrollmentRepository;
    @Mock LessonClient lessonClient;
    @Mock CourseClient courseClient;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks EnrollmentServiceImpl service;

    private Enrollment activeEnrollment() {
        Enrollment e = new Enrollment();
        e.setId(1L);
        e.setStudentId(10L);
        e.setCourseId(100L);
        e.setStatus(EnrollmentStatus.ACTIVE);
        e.setCompletionPercentage(0.0);
        e.setEnrollmentDate(LocalDateTime.now());
        return e;
    }

    // ── enroll ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Enrolling same student+course twice returns existing enrollment")
    void enroll_duplicate_returnsExisting() {
        Enrollment existing = activeEnrollment();
        when(courseClient.courseExists(100L)).thenReturn(true);
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(true);
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(existing));

        Enrollment result = service.enroll(10L, 100L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("New enrollment starts at 0% completion with ACTIVE status")
    void enroll_new_startsAtZeroPercent() {
        when(courseClient.courseExists(100L)).thenReturn(true);
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Enrollment result = service.enroll(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(result.getCompletionPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("enroll throws when course does not exist")
    void enroll_courseNotFound_throws() {
        when(courseClient.courseExists(100L)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(10L, 100L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Course not found");
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("enroll throws when course service throws FeignException")
    void enroll_feignException_throws() {
        when(courseClient.courseExists(100L)).thenThrow(feign.FeignException.class);

        assertThatThrownBy(() -> service.enroll(10L, 100L))
            .isInstanceOf(RuntimeException.class);
        verify(enrollmentRepository, never()).save(any());
    }

    // ── enrollWithPayment ─────────────────────────────────────────────

    @Test
    @DisplayName("enrollWithPayment creates new enrollment with paymentIntentId")
    void enrollWithPayment_new_savesWithPaymentId() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Enrollment result = service.enrollWithPayment(10L, 100L, "pi_test_123");

        assertThat(result.getPaymentIntentId()).isEqualTo("pi_test_123");
        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("enrollWithPayment returns existing when already enrolled")
    void enrollWithPayment_duplicate_returnsExisting() {
        Enrollment existing = activeEnrollment();
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(true);
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(existing));

        Enrollment result = service.enrollWithPayment(10L, 100L, "pi_test_456");

        assertThat(result.getId()).isEqualTo(1L);
        verify(enrollmentRepository, never()).save(any());
    }

    // ── getEnrollment ─────────────────────────────────────────────────

    @Test
    @DisplayName("getEnrollment returns enrollment when found")
    void getEnrollment_found_returns() {
        Enrollment e = activeEnrollment();
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L)).thenReturn(Optional.of(e));

        Enrollment result = service.getEnrollment(10L, 100L);

        assertThat(result.getStudentId()).isEqualTo(10L);
        assertThat(result.getCourseId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getEnrollment throws RuntimeException when not found")
    void getEnrollment_notFound_throws() {
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEnrollment(10L, 100L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Enrollment not found");
    }

    // ── isEnrolled ────────────────────────────────────────────────────

    @Test
    @DisplayName("isEnrolled returns true when enrollment exists")
    void isEnrolled_exists_returnsTrue() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(true);

        assertThat(service.isEnrolled(10L, 100L)).isTrue();
    }

    @Test
    @DisplayName("isEnrolled returns false when no enrollment")
    void isEnrolled_notExists_returnsFalse() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(10L, 100L)).thenReturn(false);

        assertThat(service.isEnrolled(10L, 100L)).isFalse();
    }

    // ── getStudentEnrollments ─────────────────────────────────────────

    @Test
    @DisplayName("getStudentEnrollments returns all enrollments for student")
    void getStudentEnrollments_returnsAll() {
        List<Enrollment> enrollments = List.of(activeEnrollment(), activeEnrollment());
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(enrollments);

        List<Enrollment> result = service.getStudentEnrollments(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getStudentEnrollments returns empty list when none found")
    void getStudentEnrollments_empty_returnsEmptyList() {
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(Collections.emptyList());

        assertThat(service.getStudentEnrollments(10L)).isEmpty();
    }

    // ── deleteEnrollmentsByCourse ─────────────────────────────────────

    @Test
    @DisplayName("deleteEnrollmentsByCourse delegates to repository")
    void deleteEnrollmentsByCourse_callsDeleteAll() {
        service.deleteEnrollmentsByCourse(100L);

        verify(enrollmentRepository).deleteAllByCourseId(100L);
    }

    // ── getMonthlyEnrollmentCounts ────────────────────────────────────

    @Test
    @DisplayName("getMonthlyEnrollmentCounts maps raw DB rows to named maps")
    void getMonthlyEnrollmentCounts_transformsRows() {
        Object[] row1 = new Object[]{5, 2026, 12L};
        Object[] row2 = new Object[]{3, 2026, 7L};
        when(enrollmentRepository.countByMonth()).thenReturn(List.of(row1, row2));

        List<Map<String, Object>> result = service.getMonthlyEnrollmentCounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("month", 5);
        assertThat(result.get(0)).containsEntry("year", 2026);
        assertThat(result.get(0)).containsEntry("count", 12L);
    }

    // ── markLessonComplete ────────────────────────────────────────────

    @Test
    @DisplayName("markLessonComplete with all lessons done sets COMPLETED status")
    void markLessonComplete_allLessons_setsCompleted() {
        Enrollment e = activeEnrollment();
        e.setCompletionPercentage(75.0);

        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.trackProgress(anyLong(), anyLong(), anyString(), any()))
            .thenReturn(Map.of());
        when(lessonClient.getCompletedLessonCount(1L)).thenReturn(4L);
        when(lessonClient.getLessons(100L)).thenReturn(List.of(
            Map.of("id", 1, "orderIndex", 0),
            Map.of("id", 2, "orderIndex", 1),
            Map.of("id", 3, "orderIndex", 2),
            Map.of("id", 4, "orderIndex", 3)
        ));
        when(lessonClient.getLessonCount(100L)).thenReturn(4L);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Enrollment result = service.markLessonComplete(100L, 4L, 10L);

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(result.getCompletionPercentage()).isEqualTo(100.0);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markLessonComplete partial completion calculates correct percentage")
    void markLessonComplete_partialCompletion_correctPercentage() {
        Enrollment e = activeEnrollment();

        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.trackProgress(anyLong(), anyLong(), anyString(), any()))
            .thenReturn(Map.of());
        when(lessonClient.getCompletedLessonCount(1L)).thenReturn(2L);
        when(lessonClient.getLessons(100L)).thenReturn(List.of(
            Map.of("id", 1, "orderIndex", 0),
            Map.of("id", 2, "orderIndex", 1),
            Map.of("id", 3, "orderIndex", 2),
            Map.of("id", 4, "orderIndex", 3)
        ));
        when(lessonClient.getLessonCount(100L)).thenReturn(4L);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Enrollment result = service.markLessonComplete(100L, 1L, 10L);

        assertThat(result.getCompletionPercentage()).isEqualTo(50.0);
        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("markLessonComplete advances currentLessonId to next lesson")
    void markLessonComplete_advancesCurrentLesson() {
        Enrollment e = activeEnrollment();

        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.trackProgress(anyLong(), anyLong(), anyString(), any()))
            .thenReturn(Map.of());
        when(lessonClient.getCompletedLessonCount(1L)).thenReturn(1L);
        // Must use ArrayList so the service can call .sort() on it
        List<Map<String, Object>> lessons = new java.util.ArrayList<>();
        lessons.add(new java.util.HashMap<>(Map.of("id", 1, "orderIndex", 0)));
        lessons.add(new java.util.HashMap<>(Map.of("id", 2, "orderIndex", 1)));
        lessons.add(new java.util.HashMap<>(Map.of("id", 3, "orderIndex", 2)));
        when(lessonClient.getLessons(100L)).thenReturn(lessons);
        when(lessonClient.getLessonCount(100L)).thenReturn(3L);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Enrollment result = service.markLessonComplete(100L, 1L, 10L);

        assertThat(result.getCurrentLessonId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("markLessonComplete throws when lesson tracking fails")
    void markLessonComplete_lessonServiceFails_throws() {
        Enrollment e = activeEnrollment();
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.trackProgress(anyLong(), anyLong(), anyString(), any()))
            .thenThrow(new RuntimeException("Lesson service down"));

        assertThatThrownBy(() -> service.markLessonComplete(100L, 1L, 10L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to record lesson progress");
    }

    @Test
    @DisplayName("markLessonComplete publishes completion event when 100% done")
    void markLessonComplete_100percent_publishesEvent() {
        Enrollment e = activeEnrollment();

        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.trackProgress(anyLong(), anyLong(), anyString(), any()))
            .thenReturn(Map.of());
        when(lessonClient.getCompletedLessonCount(1L)).thenReturn(2L);
        when(lessonClient.getLessons(100L)).thenReturn(List.of(
            Map.of("id", 1, "orderIndex", 0),
            Map.of("id", 2, "orderIndex", 1)
        ));
        when(lessonClient.getLessonCount(100L)).thenReturn(2L);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.markLessonComplete(100L, 2L, 10L);

        verify(rabbitTemplate).convertAndSend(
            eq("enrollment.exchange"), eq("enrollment.completed"), (Object) any());
    }

    // ── trackTimeSpent ────────────────────────────────────────────────

    @Test
    @DisplayName("trackTimeSpent delegates to lessonClient when enrollment exists")
    void trackTimeSpent_withEnrollment_callsClient() {
        Enrollment e = activeEnrollment();
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));

        service.trackTimeSpent(100L, 5L, 10L, 120);

        verify(lessonClient).trackTimeOnly(1L, 5L, 120L);
    }

    @Test
    @DisplayName("trackTimeSpent does nothing when enrollment not found")
    void trackTimeSpent_noEnrollment_doesNothing() {
        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.empty());

        service.trackTimeSpent(100L, 5L, 10L, 120);

        verifyNoInteractions(lessonClient);
    }

    // ── getEnrollmentsByCourse / getAllEnrollments ─────────────────────

    @Test
    @DisplayName("getEnrollmentsByCourse returns all for that course")
    void getEnrollmentsByCourse_returnsList() {
        when(enrollmentRepository.findByCourseId(100L))
            .thenReturn(List.of(activeEnrollment(), activeEnrollment()));

        assertThat(service.getEnrollmentsByCourse(100L)).hasSize(2);
    }

    @Test
    @DisplayName("getAllEnrollments returns full list from repository")
    void getAllEnrollments_returnsList() {
        when(enrollmentRepository.findAll()).thenReturn(List.of(activeEnrollment()));

        assertThat(service.getAllEnrollments()).hasSize(1);
    }

    // ── getByCertificateId ────────────────────────────────────────────

    @Test
    @DisplayName("getByCertificateId returns enrollment when found")
    void getByCertificateId_found_returns() {
        Enrollment e = activeEnrollment();
        e.setCertificateId("YBRY-2026-001");
        when(enrollmentRepository.findByCertificateId("YBRY-2026-001")).thenReturn(Optional.of(e));

        assertThat(service.getByCertificateId("YBRY-2026-001").getCertificateId())
            .isEqualTo("YBRY-2026-001");
    }

    @Test
    @DisplayName("getByCertificateId throws when not found")
    void getByCertificateId_notFound_throws() {
        when(enrollmentRepository.findByCertificateId("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCertificateId("NONE"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Certificate not found");
    }

    // ── updateCertificateId / updateCertificate ───────────────────────

    @Test
    @DisplayName("updateCertificateId persists certificateId")
    void updateCertificateId_found_persists() {
        Enrollment e = activeEnrollment();
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(e));
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateCertificateId(1L, "YBRY-2026-002");

        assertThat(e.getCertificateId()).isEqualTo("YBRY-2026-002");
        verify(enrollmentRepository).save(e);
    }

    @Test
    @DisplayName("updateCertificateId throws when enrollment not found")
    void updateCertificateId_notFound_throws() {
        when(enrollmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCertificateId(99L, "cert"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Enrollment not found");
    }

    @Test
    @DisplayName("updateCertificate persists certificateId to enrollment")
    void updateCertificate_persistsCertId() {
        Enrollment e = activeEnrollment();
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(e));
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateCertificate(1L, "YBRY-2026-TEST-001");

        assertThat(e.getCertificateId()).isEqualTo("YBRY-2026-TEST-001");
        verify(enrollmentRepository).save(e);
    }

    // ── getStudentDashboard ───────────────────────────────────────────

    @Test
    @DisplayName("getStudentDashboard returns zeroes for student with no enrollments")
    void getStudentDashboard_noEnrollments_returnsZeros() {
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(Collections.emptyList());

        Map<String, Object> dash = service.getStudentDashboard(10L);

        assertThat(dash)
            .containsEntry("totalEnrolled", 0)
            .containsEntry("totalCompleted", 0)
            .containsEntry("averageProgress", 0.0);
    }

    @Test
    @DisplayName("getStudentDashboard counts completed and in-progress enrollments correctly")
    void getStudentDashboard_mixedEnrollments_countsCorrectly() {
        Enrollment completed = activeEnrollment();
        completed.setStatus(EnrollmentStatus.COMPLETED);
        completed.setCompletionPercentage(100.0);
        completed.setCertificateId("CERT-001");

        Enrollment inProgress = activeEnrollment();
        inProgress.setId(2L);
        inProgress.setCompletionPercentage(40.0);

        when(enrollmentRepository.findByStudentId(10L))
            .thenReturn(List.of(completed, inProgress));
        when(courseClient.getCourse(anyLong())).thenReturn(Map.of("title", "Java Basics"));
        when(lessonClient.getLessonCount(anyLong())).thenReturn(10L);
        when(lessonClient.getCompletedLessonCount(anyLong())).thenReturn(5L);

        Map<String, Object> dash = service.getStudentDashboard(10L);

        assertThat(dash)
            .containsEntry("totalEnrolled", 2)
            .containsEntry("totalCompleted", 1)
            .containsEntry("totalInProgress", 1)
            .containsEntry("totalCertificates", 1L);
        assertThat((Double) dash.get("averageProgress")).isEqualTo(70.0);
    }

    // ── getCourseProgress ─────────────────────────────────────────────

    @Test
    @DisplayName("getCourseProgress returns enrollment progress map")
    void getCourseProgress_returnsMap() {
        Enrollment e = activeEnrollment();
        e.setCompletionPercentage(60.0);
        e.setCurrentLessonId(3L);

        when(enrollmentRepository.findByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(e));
        when(lessonClient.getProgressByEnrollment(1L)).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.getCourseProgress(100L, 10L);

        assertThat(result)
            .containsEntry("courseId", 100L)
            .containsEntry("completionPercentage", 60.0)
            .containsEntry("currentLessonId", 3L)
            .containsEntry("enrollmentStatus", "ACTIVE");
    }
}
