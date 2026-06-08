package tn.esprit.lessonservice.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.lessonservice.clients.CourseClient;
import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonProgress;
import tn.esprit.lessonservice.entities.LessonType;
import tn.esprit.lessonservice.entities.ProgressStatus;
import tn.esprit.lessonservice.repositories.LessonProgressRepository;
import tn.esprit.lessonservice.repositories.LessonRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock CourseClient courseClient;
    @InjectMocks LessonServiceImpl service;

    private Lesson sampleLesson(Long id, Long courseId) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setCourseId(courseId);
        l.setTitle("Intro to Java");
        l.setType(LessonType.VIDEO_UPLOAD);
        l.setOrderIndex(1);
        l.setDurationMinutes(30);
        return l;
    }

    // ── getLessonsByCourse ────────────────────────────────────────────

    @Test
    @DisplayName("getLessonsByCourse returns all lessons for the given course")
    void getLessonsByCourse_returnsList() {
        when(lessonRepository.findByCourseIdWithContents(10L))
            .thenReturn(List.of(sampleLesson(1L, 10L), sampleLesson(2L, 10L)));

        List<Lesson> result = service.getLessonsByCourse(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCourseId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getLessonsByCourse returns empty list when course has no lessons")
    void getLessonsByCourse_empty_returnsEmptyList() {
        when(lessonRepository.findByCourseIdWithContents(10L)).thenReturn(Collections.emptyList());

        assertThat(service.getLessonsByCourse(10L)).isEmpty();
    }

    // ── getLessonById ─────────────────────────────────────────────────

    @Test
    @DisplayName("getLessonById returns lesson when found")
    void getLessonById_found_returns() {
        Lesson lesson = sampleLesson(1L, 10L);
        when(lessonRepository.findByIdWithContents(1L)).thenReturn(Optional.of(lesson));

        Lesson result = service.getLessonById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Intro to Java");
    }

    @Test
    @DisplayName("getLessonById throws when lesson is not found")
    void getLessonById_notFound_throws() {
        when(lessonRepository.findByIdWithContents(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLessonById(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    // ── getLessonByIdAndCourse ────────────────────────────────────────

    @Test
    @DisplayName("getLessonByIdAndCourse returns lesson when found for course")
    void getLessonByIdAndCourse_found_returns() {
        Lesson lesson = sampleLesson(1L, 10L);
        when(lessonRepository.findByIdAndCourseId(1L, 10L)).thenReturn(Optional.of(lesson));

        Lesson result = service.getLessonByIdAndCourse(1L, 10L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getLessonByIdAndCourse throws when not found in course")
    void getLessonByIdAndCourse_notFound_throws() {
        when(lessonRepository.findByIdAndCourseId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLessonByIdAndCourse(1L, 99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    // ── createLesson ──────────────────────────────────────────────────

    @Test
    @DisplayName("createLesson verifies course exists before saving")
    void createLesson_validCourse_saves() {
        when(courseClient.courseExists(10L)).thenReturn(true);
        when(lessonRepository.findMaxOrderIndexByCourseId(10L)).thenReturn(Optional.of(2));
        Lesson lesson = sampleLesson(null, null);
        lesson.setOrderIndex(null);
        when(lessonRepository.save(any())).thenAnswer(i -> {
            Lesson l = i.getArgument(0);
            l.setId(1L);
            return l;
        });

        Lesson result = service.createLesson(10L, lesson);

        assertThat(result.getCourseId()).isEqualTo(10L);
        assertThat(result.getOrderIndex()).isEqualTo(3);
        verify(lessonRepository).save(lesson);
    }

    @Test
    @DisplayName("createLesson throws when course does not exist")
    void createLesson_courseNotFound_throws() {
        when(courseClient.courseExists(999L)).thenReturn(false);

        Lesson notFound = sampleLesson(null, null);
        assertThatThrownBy(() -> service.createLesson(999L, notFound))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");

        verify(lessonRepository, never()).save(any());
    }

    @Test
    @DisplayName("createLesson uses orderIndex 1 when no lessons exist for course")
    void createLesson_firstLesson_orderIndex1() {
        when(courseClient.courseExists(10L)).thenReturn(true);
        when(lessonRepository.findMaxOrderIndexByCourseId(10L)).thenReturn(Optional.empty());
        Lesson lesson = sampleLesson(null, null);
        lesson.setOrderIndex(null);
        when(lessonRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Lesson result = service.createLesson(10L, lesson);

        assertThat(result.getOrderIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("createLesson keeps provided orderIndex when explicitly set")
    void createLesson_explicitOrderIndex_preserved() {
        when(courseClient.courseExists(10L)).thenReturn(true);
        Lesson lesson = sampleLesson(null, null);
        lesson.setOrderIndex(5);
        when(lessonRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Lesson result = service.createLesson(10L, lesson);

        assertThat(result.getOrderIndex()).isEqualTo(5);
    }

    // ── deleteLesson ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteLesson removes the lesson when found in the course")
    void deleteLesson_existing_removes() {
        Lesson lesson = sampleLesson(1L, 10L);
        when(lessonRepository.findByIdAndCourseId(1L, 10L)).thenReturn(Optional.of(lesson));

        service.deleteLesson(10L, 1L);

        verify(lessonRepository).delete(lesson);
    }

    @Test
    @DisplayName("deleteLesson throws when lesson not found in course")
    void deleteLesson_notFound_throws() {
        when(lessonRepository.findByIdAndCourseId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteLesson(10L, 99L))
            .isInstanceOf(RuntimeException.class);
        verify(lessonRepository, never()).delete(any());
    }

    // ── countLessonsByCourse ──────────────────────────────────────────

    @Test
    @DisplayName("countLessonsByCourse delegates to repository")
    void countLessonsByCourse_delegatesToRepo() {
        when(lessonRepository.countByCourseId(10L)).thenReturn(5L);

        assertThat(service.countLessonsByCourse(10L)).isEqualTo(5L);
    }

    @Test
    @DisplayName("countLessonsByCourse returns 0 when no lessons")
    void countLessonsByCourse_zero() {
        when(lessonRepository.countByCourseId(10L)).thenReturn(0L);

        assertThat(service.countLessonsByCourse(10L)).isZero();
    }

    // ── trackProgress ─────────────────────────────────────────────────

    @Test
    @DisplayName("trackProgress creates new progress when none exists")
    void trackProgress_newProgress_saved() {
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackProgress(1L, 1L, "IN_PROGRESS", null);

        assertThat(result.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        verify(lessonProgressRepository).save(any(LessonProgress.class));
    }

    @Test
    @DisplayName("trackProgress sets completedAt when status transitions to COMPLETED")
    void trackProgress_completed_setsCompletedAt() {
        LessonProgress existing = LessonProgress.builder()
            .enrollmentId(1L).lessonId(1L).status(ProgressStatus.IN_PROGRESS).build();
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackProgress(1L, 1L, "COMPLETED", null);

        assertThat(result.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("trackProgress does not overwrite existing completedAt when already completed")
    void trackProgress_alreadyCompleted_doesNotResetCompletedAt() {
        LocalDateTime originalCompletedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LessonProgress existing = LessonProgress.builder()
            .enrollmentId(1L).lessonId(1L)
            .status(ProgressStatus.COMPLETED)
            .completedAt(originalCompletedAt).build();
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackProgress(1L, 1L, "COMPLETED", null);

        assertThat(result.getCompletedAt()).isEqualTo(originalCompletedAt);
    }

    @Test
    @DisplayName("trackProgress accumulates timeSpent on repeated calls")
    void trackProgress_accumulatesTime() {
        LessonProgress existing = LessonProgress.builder()
            .enrollmentId(1L).lessonId(1L)
            .status(ProgressStatus.IN_PROGRESS).timeSpentSeconds(120L).build();
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackProgress(1L, 1L, "IN_PROGRESS", 60L);

        assertThat(result.getTimeSpentSeconds()).isEqualTo(180L);
    }

    @Test
    @DisplayName("trackProgress handles null timeSpent without adding to existing value")
    void trackProgress_nullTimeSpent_doesNotAdd() {
        LessonProgress existing = LessonProgress.builder()
            .enrollmentId(1L).lessonId(1L)
            .status(ProgressStatus.IN_PROGRESS).timeSpentSeconds(100L).build();
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackProgress(1L, 1L, "IN_PROGRESS", null);

        assertThat(result.getTimeSpentSeconds()).isEqualTo(100L);
    }

    // ── countCompletedLessons ─────────────────────────────────────────

    @Test
    @DisplayName("countCompletedLessons delegates to repository with COMPLETED status")
    void countCompletedLessons_delegatesToRepo() {
        when(lessonProgressRepository.countByEnrollmentIdAndStatus(1L, ProgressStatus.COMPLETED))
            .thenReturn(4L);

        assertThat(service.countCompletedLessons(1L)).isEqualTo(4L);
    }

    @Test
    @DisplayName("countCompletedLessons returns 0 when none completed")
    void countCompletedLessons_zero() {
        when(lessonProgressRepository.countByEnrollmentIdAndStatus(1L, ProgressStatus.COMPLETED))
            .thenReturn(0L);

        assertThat(service.countCompletedLessons(1L)).isZero();
    }

    // ── getProgressByEnrollment ───────────────────────────────────────

    @Test
    @DisplayName("getProgressByEnrollment returns progress records for enrollment")
    void getProgressByEnrollment_returnsList() {
        LessonProgress p = new LessonProgress();
        p.setEnrollmentId(1L);
        when(lessonProgressRepository.findByEnrollmentId(1L)).thenReturn(List.of(p));

        List<LessonProgress> result = service.getProgressByEnrollment(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnrollmentId()).isEqualTo(1L);
    }

    // ── trackTimeOnly ─────────────────────────────────────────────────

    @Test
    @DisplayName("trackTimeOnly creates new progress with NOT_STARTED when none exists")
    void trackTimeOnly_newProgress_createsWithNotStarted() {
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackTimeOnly(1L, 1L, 30L);

        assertThat(result.getStatus()).isEqualTo(ProgressStatus.NOT_STARTED);
        assertThat(result.getTimeSpentSeconds()).isEqualTo(30L);
    }

    @Test
    @DisplayName("trackTimeOnly accumulates time on existing progress")
    void trackTimeOnly_existingProgress_accumulatesTime() {
        LessonProgress existing = LessonProgress.builder()
            .enrollmentId(1L).lessonId(1L)
            .status(ProgressStatus.IN_PROGRESS).timeSpentSeconds(60L).build();
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(1L, 1L))
            .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LessonProgress result = service.trackTimeOnly(1L, 1L, 45L);

        assertThat(result.getTimeSpentSeconds()).isEqualTo(105L);
    }

    // ── searchLessonsByTitle ──────────────────────────────────────────

    @Test
    @DisplayName("searchLessonsByTitle returns matching lessons case-insensitively")
    void searchLessonsByTitle_returnsMatches() {
        when(lessonRepository.findByTitleContainingIgnoreCase("java"))
            .thenReturn(List.of(sampleLesson(1L, 10L)));

        List<Lesson> result = service.searchLessonsByTitle("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).containsIgnoringCase("Java");
    }

    @Test
    @DisplayName("searchLessonsByTitle returns empty when no match")
    void searchLessonsByTitle_noMatch_returnsEmpty() {
        when(lessonRepository.findByTitleContainingIgnoreCase("python"))
            .thenReturn(Collections.emptyList());

        assertThat(service.searchLessonsByTitle("python")).isEmpty();
    }

    // ── getProgressByEnrollmentIds ────────────────────────────────────

    @Test
    @DisplayName("getProgressByEnrollmentIds returns empty for null or empty input")
    void getProgressByEnrollmentIds_emptyInput_returnsEmpty() {
        assertThat(service.getProgressByEnrollmentIds(null)).isEmpty();
        assertThat(service.getProgressByEnrollmentIds(Collections.emptyList())).isEmpty();
        verifyNoInteractions(lessonProgressRepository);
    }

    @Test
    @DisplayName("getProgressByEnrollmentIds delegates to repository with ids")
    void getProgressByEnrollmentIds_withIds_delegatesToRepo() {
        LessonProgress p = new LessonProgress();
        p.setEnrollmentId(1L);
        when(lessonProgressRepository.findByEnrollmentIdIn(List.of(1L, 2L)))
            .thenReturn(List.of(p));

        List<LessonProgress> result = service.getProgressByEnrollmentIds(List.of(1L, 2L));

        assertThat(result).hasSize(1);
    }

    // ── deleteProgressByEnrollmentIds ─────────────────────────────────

    @Test
    @DisplayName("deleteProgressByEnrollmentIds delegates to repository")
    void deleteProgressByEnrollmentIds_callsRepo() {
        service.deleteProgressByEnrollmentIds(List.of(1L, 2L, 3L));

        verify(lessonProgressRepository).deleteAllByEnrollmentIdIn(List.of(1L, 2L, 3L));
    }

    // ── deleteAllLessonsByCourse ──────────────────────────────────────

    @Test
    @DisplayName("deleteAllLessonsByCourse deletes all lessons for a course")
    void deleteAllLessonsByCourse_deletesAll() {
        List<Lesson> lessons = List.of(sampleLesson(1L, 10L), sampleLesson(2L, 10L));
        when(lessonRepository.findByCourseId(10L)).thenReturn(lessons);

        service.deleteAllLessonsByCourse(10L);

        verify(lessonRepository).deleteAll(lessons);
    }

    @Test
    @DisplayName("deleteAllLessonsByCourse does nothing when course has no lessons")
    void deleteAllLessonsByCourse_noLessons_deletesEmptyList() {
        when(lessonRepository.findByCourseId(10L)).thenReturn(Collections.emptyList());

        service.deleteAllLessonsByCourse(10L);

        verify(lessonRepository).deleteAll(Collections.emptyList());
    }

    // ── getActiveEnrollmentIdsSince ───────────────────────────────────

    @Test
    @DisplayName("getActiveEnrollmentIdsSince delegates to repository")
    void getActiveEnrollmentIdsSince_delegatesToRepo() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(lessonProgressRepository.findEnrollmentIdsWithActivitySince(since))
            .thenReturn(List.of(1L, 2L, 3L));

        List<Long> result = service.getActiveEnrollmentIdsSince(since);

        assertThat(result).containsExactly(1L, 2L, 3L);
    }
}
