package tn.esprit.tpfoyer.Services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import tn.esprit.tpfoyer.Dto.CourseRequestDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.CourseReviewRepository;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Clients.LessonClient;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock CourseRepository courseRepository;
    @Mock LessonClient lessonClient;
    @Mock EnrollmentClient enrollmentClient;
    @Mock IFileStorageService fileStorageService;
    @Mock IAiSearchService aiSearchService;
    @Mock CourseReviewRepository courseReviewRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks CourseServiceImpl service;

    private Course sampleCourse() {
        Course c = new Course();
        c.setId(1L);
        c.setTitle("Test Course");
        c.setIsPublished(false);
        c.setInstructorId(5L);
        c.setCategory(CourseCategory.PROGRAMMING);
        c.setLevel(CourseLevel.BEGINNER);
        c.setPrice(BigDecimal.valueOf(49.99));
        return c;
    }

    // ── getCourseStatsByCategory ───────────────────────────────────────

    @Test
    @DisplayName("getCourseStatsByCategory groups courses by category")
    void getCourseStatsByCategory_groupsByCategory() {
        Course c = sampleCourse();
        c.setCategory(CourseCategory.PROGRAMMING);
        when(courseRepository.findAll()).thenReturn(List.of(c));

        Map<String, Long> result = service.getCourseStatsByCategory();

        assertThat(result).containsEntry("PROGRAMMING", 1L);
    }

    @Test
    @DisplayName("getCourseStatsByCategory returns empty map when no courses have categories")
    void getCourseStatsByCategory_noCourses_returnsEmpty() {
        when(courseRepository.findAll()).thenReturn(List.of());

        Map<String, Long> result = service.getCourseStatsByCategory();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCourseStatsByCategory counts multiple categories correctly")
    void getCourseStatsByCategory_multipleCourses_correctCounts() {
        Course c1 = new Course(); c1.setCategory(CourseCategory.PROGRAMMING);
        Course c2 = new Course(); c2.setCategory(CourseCategory.PROGRAMMING);
        Course c3 = new Course(); c3.setCategory(CourseCategory.DESIGN);
        Course c4 = new Course(); // null category — should be excluded
        when(courseRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4));

        Map<String, Long> result = service.getCourseStatsByCategory();

        assertThat(result).containsEntry("PROGRAMMING", 2L).containsEntry("DESIGN", 1L)
            .doesNotContainKey(null);
    }

    // ── existsById ────────────────────────────────────────────────────

    @Test
    @DisplayName("existsById returns true when course exists")
    void existsById_returnsTrue() {
        when(courseRepository.existsById(1L)).thenReturn(true);
        assertThat(service.existsById(1L)).isTrue();
    }

    @Test
    @DisplayName("existsById returns false when course does not exist")
    void existsById_returnsFalse() {
        when(courseRepository.existsById(999L)).thenReturn(false);
        assertThat(service.existsById(999L)).isFalse();
    }

    // ── getCourseById ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCourseById throws when course not found")
    void getCourseById_notFound_throws() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCourseById(999L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getCourseById returns detail DTO when course exists")
    void getCourseById_found_returnsDetailDTO() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(lessonClient.getLessonCount(1L)).thenReturn(3L);
        when(lessonClient.getLessonsByCourse(1L)).thenReturn(List.of(
            Map.of("id", 1, "title", "Lesson 1", "type", "VIDEO_UPLOAD",
                   "orderIndex", 0, "contents", Collections.emptyList())
        ));

        var result = service.getCourseById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Test Course");
        assertThat(result.getLessons()).hasSize(1);
    }

    @Test
    @DisplayName("getCourseById handles lesson service failure gracefully with empty lesson list")
    void getCourseById_lessonServiceFails_returnsEmptyLessons() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(lessonClient.getLessonCount(1L)).thenReturn(0L);
        when(lessonClient.getLessonsByCourse(1L)).thenThrow(new RuntimeException("Lesson service down"));

        var result = service.getCourseById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getLessons()).isEmpty();
    }

    // ── createCourse ──────────────────────────────────────────────────

    @Test
    @DisplayName("createCourse without thumbnail or certificate saves and returns DTO")
    void createCourse_basic_savesAndReturnsDTO() {
        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Java Basics");
        dto.setDescription("Learn Java");
        dto.setPrice(BigDecimal.valueOf(29.99));
        dto.setCategory(CourseCategory.PROGRAMMING);
        dto.setLevel(CourseLevel.BEGINNER);
        dto.setInstructorId(10L);
        dto.setIsPublished(false);
        dto.setOffersCertificate(false);

        Course saved = new Course();
        saved.setId(42L);
        saved.setTitle("Java Basics");
        saved.setCategory(CourseCategory.PROGRAMMING);
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(lessonClient.getLessonCount(42L)).thenReturn(0L);

        var result = service.createCourse(dto, null, null);

        assertThat(result).isNotNull();
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("createCourse with offersCertificate=true but no file throws")
    void createCourse_certificateRequired_throws() {
        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Advanced Java");
        dto.setInstructorId(10L);
        dto.setOffersCertificate(true);

        assertThatThrownBy(() -> service.createCourse(dto, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Certificate file is required");
    }

    @Test
    @DisplayName("createCourse with isPublished null defaults to false")
    void createCourse_nullPublished_defaultsFalse() {
        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Course");
        dto.setInstructorId(1L);
        dto.setIsPublished(null);
        dto.setOffersCertificate(false);

        Course saved = sampleCourse();
        saved.setIsPublished(false);
        when(courseRepository.save(any())).thenReturn(saved);
        when(lessonClient.getLessonCount(any())).thenReturn(0L);

        var result = service.createCourse(dto, null, null);

        assertThat(result.getIsPublished()).isFalse();
    }

    // ── updateCourse ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateCourse by owner updates and saves course")
    void updateCourse_byOwner_updatesAndSaves() {
        Course existing = sampleCourse();
        existing.setOffersCertificate(false);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(courseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lessonClient.getLessonCount(1L)).thenReturn(2L);

        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Updated Title");
        dto.setDescription("New desc");
        dto.setPrice(BigDecimal.valueOf(59.99));
        dto.setCategory(CourseCategory.DESIGN);
        dto.setLevel(CourseLevel.INTERMEDIATE);
        dto.setInstructorId(5L);
        dto.setIsPublished(true);
        dto.setOffersCertificate(false);

        var result = service.updateCourse(1L, dto, null, null, 5L, "INSTRUCTOR");

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        verify(courseRepository).save(existing);
    }

    @Test
    @DisplayName("updateCourse by non-owner instructor throws access denied")
    void updateCourse_byNonOwner_throws() {
        Course existing = sampleCourse(); // instructorId = 5L
        when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));

        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Hacked");

        assertThatThrownBy(() -> service.updateCourse(1L, dto, null, null, 99L, "INSTRUCTOR"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateCourse by admin succeeds regardless of ownership")
    void updateCourse_byAdmin_succeeds() {
        Course existing = sampleCourse();
        existing.setOffersCertificate(false);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(courseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lessonClient.getLessonCount(1L)).thenReturn(0L);

        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setTitle("Admin Updated");
        dto.setInstructorId(5L);
        dto.setOffersCertificate(false);

        assertThatNoException().isThrownBy(
            () -> service.updateCourse(1L, dto, null, null, 999L, "ADMIN"));
    }

    // ── deleteCourse ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCourse by non-owner instructor throws access denied")
    void deleteCourse_nonOwner_throws() {
        Course c = sampleCourse(); // instructorId = 5L
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteCourse(1L, 99L, "INSTRUCTOR"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("deleteCourse by admin succeeds regardless of ownership")
    void deleteCourse_admin_succeeds() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenReturn(Collections.emptyList());

        assertThatNoException().isThrownBy(() -> service.deleteCourse(1L, 999L, "ADMIN"));
        verify(courseRepository).delete(c);
    }

    @Test
    @DisplayName("deleteCourse by owner instructor succeeds")
    void deleteCourse_ownerInstructor_succeeds() {
        Course c = sampleCourse(); // instructorId = 5L
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenReturn(Collections.emptyList());

        assertThatNoException().isThrownBy(() -> service.deleteCourse(1L, 5L, "INSTRUCTOR"));
        verify(courseRepository).delete(c);
    }

    @Test
    @DisplayName("deleteCourse publishes course.deleted event to RabbitMQ")
    void deleteCourse_publishesDomainEvent() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenReturn(Collections.emptyList());

        service.deleteCourse(1L, 5L, "INSTRUCTOR");

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    @DisplayName("deleteCourse cleans up reviews, enrollments and files")
    void deleteCourse_cleansUpDependencies() {
        Course c = sampleCourse();
        c.setThumbnailUrl("thumb.jpg");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenReturn(Collections.emptyList());

        service.deleteCourse(1L, 5L, "INSTRUCTOR");

        verify(courseReviewRepository).deleteAllByCourseId(1L);
        verify(enrollmentClient).deleteEnrollmentsByCourse(1L);
        verify(fileStorageService).deleteFile("thumb.jpg");
    }

    // ── togglePublish ─────────────────────────────────────────────────

    @Test
    @DisplayName("togglePublish sets isPublished to true")
    void togglePublish_setsPublished() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        Map<String, Object> result = service.togglePublish(1L, true, 5L, "INSTRUCTOR");

        assertThat(result).containsEntry("isPublished", true);
    }

    @Test
    @DisplayName("togglePublish sets isPublished to false")
    void togglePublish_setsUnpublished() {
        Course c = sampleCourse();
        c.setIsPublished(true);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        Map<String, Object> result = service.togglePublish(1L, false, 5L, "INSTRUCTOR");

        assertThat(result).containsEntry("isPublished", false);
    }

    @Test
    @DisplayName("togglePublish by non-owner throws access denied")
    void togglePublish_nonOwner_throws() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.togglePublish(1L, true, 99L, "INSTRUCTOR"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("own courses");
    }

    @Test
    @DisplayName("Admin can publish any course regardless of ownership")
    void togglePublish_adminCanPublishAny() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));

        Map<String, Object> result = service.togglePublish(1L, true, 99L, "ADMIN");

        assertThat(result).containsEntry("isPublished", true);
    }

    // ── getCourseAnalytics ────────────────────────────────────────────

    @Test
    @DisplayName("getCourseAnalytics returns DTO with correct enrollment counts")
    void getCourseAnalytics_withEnrollments_returnsCorrectCounts() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(lessonClient.getLessonsByCourse(1L)).thenReturn(Collections.emptyList());
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenReturn(List.of(
            Map.of("id", 1, "status", "COMPLETED", "completionPercentage", 100.0),
            Map.of("id", 2, "status", "ACTIVE", "completionPercentage", 50.0)
        ));
        when(lessonClient.getActiveEnrollmentIdsSince(anyString())).thenReturn(Collections.emptyList());
        when(courseReviewRepository.countByCourseId(1L)).thenReturn(5L);

        var result = service.getCourseAnalytics(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTotalEnrollments()).isEqualTo(2);
        assertThat(result.getCompletedStudents()).isEqualTo(1);
        assertThat(result.getAverageProgressPercentage()).isEqualTo(75.0);
        assertThat(result.getTotalReviews()).isEqualTo(5);
    }

    @Test
    @DisplayName("getCourseAnalytics returns zero values when enrollment service is down")
    void getCourseAnalytics_enrollmentServiceDown_returnsZeros() {
        Course c = sampleCourse();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c));
        when(lessonClient.getLessonsByCourse(1L)).thenReturn(Collections.emptyList());
        when(enrollmentClient.getEnrollmentsByCourse(1L)).thenThrow(new RuntimeException("down"));
        when(courseReviewRepository.countByCourseId(1L)).thenReturn(0L);

        var result = service.getCourseAnalytics(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTotalEnrollments()).isEqualTo(0);
    }

    @Test
    @DisplayName("getCourseAnalytics throws when course not found")
    void getCourseAnalytics_courseNotFound_throws() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCourseAnalytics(999L))
            .isInstanceOf(RuntimeException.class);
    }

    // ── getAllCourses ─────────────────────────────────────────────────

    @Test
    @DisplayName("getAllCourses returns paginated result with lesson counts")
    @SuppressWarnings("unchecked")
    void getAllCourses_returnsPaginatedResults() {
        Course c = sampleCourse();
        Page<Course> page = new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1);
        when(courseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(lessonClient.getLessonCount(1L)).thenReturn(5L);

        var result = service.getAllCourses(null, null, null, null, null, null, null,
            PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLessonCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getAllCourses handles lesson service failure with 0 lesson count")
    @SuppressWarnings("unchecked")
    void getAllCourses_lessonServiceFails_usesZero() {
        Course c = sampleCourse();
        Page<Course> page = new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1);
        when(courseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(lessonClient.getLessonCount(1L)).thenThrow(new RuntimeException("down"));

        var result = service.getAllCourses(null, null, null, null, null, null, null,
            PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getLessonCount()).isEqualTo(0);
    }
}
