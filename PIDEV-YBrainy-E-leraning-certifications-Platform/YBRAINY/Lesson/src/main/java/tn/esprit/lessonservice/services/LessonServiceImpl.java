package tn.esprit.lessonservice.services;

import tn.esprit.lessonservice.entities.*;
import tn.esprit.lessonservice.repositories.*;
import tn.esprit.lessonservice.clients.CourseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LessonServiceImpl implements ILessonService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CourseClient courseClient;

    @Override
    public List<Lesson> getLessonsByCourse(Long courseId) {
        return lessonRepository.findByCourseIdWithContents(courseId);
    }

    @Override
    public Lesson getLessonById(Long id) {
        return lessonRepository.findByIdWithContents(id)
            .orElseThrow(() -> new RuntimeException("Lesson not found: " + id));
    }

    @Override
    public Lesson getLessonByIdAndCourse(Long id, Long courseId) {
        return lessonRepository.findByIdAndCourseId(id, courseId)
            .orElseThrow(() -> new RuntimeException("Lesson not found"));
    }

    @Override
    @Transactional
    public Lesson createLesson(Long courseId, Lesson lesson) {
        // Verify course exists via Feign
        if (!courseClient.courseExists(courseId)) {
            throw new IllegalArgumentException("Course not found: " + courseId);
        }
        lesson.setCourseId(courseId);
        if (lesson.getOrderIndex() == null) {
            int maxIndex = lessonRepository
                .findMaxOrderIndexByCourseId(courseId).orElse(0);
            lesson.setOrderIndex(maxIndex + 1);
        }
        if (lesson.getContents() != null) {
            lesson.getContents().forEach(c -> c.setLesson(lesson));
        }
        return lessonRepository.save(lesson);
    }

    @Override
    @Transactional
    public Lesson updateLesson(Long courseId, Long lessonId, Lesson lesson) {
        Lesson existing = getLessonByIdAndCourse(lessonId, courseId);
        existing.setTitle(lesson.getTitle());
        existing.setDescription(lesson.getDescription());
        existing.setType(lesson.getType());
        existing.setContentUrl(lesson.getContentUrl());
        existing.setOrderIndex(lesson.getOrderIndex());
        existing.setDurationMinutes(lesson.getDurationMinutes());
        if (lesson.getContents() != null) {
            List<LessonContent> newContents = new ArrayList<>(lesson.getContents());
            existing.getContents().clear();
            existing.getContents().addAll(newContents);
            existing.getContents().forEach(c -> c.setLesson(existing));
        }
        return lessonRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteLesson(Long courseId, Long lessonId) {
        Lesson lesson = getLessonByIdAndCourse(lessonId, courseId);
        lessonRepository.delete(lesson);
    }

    @Override
    public long countLessonsByCourse(Long courseId) {
        return lessonRepository.countByCourseId(courseId);
    }

    @Override
    @Transactional
    public LessonProgress trackProgress(Long enrollmentId, Long lessonId,
                                         String status, Long timeSpent) {
        LessonProgress progress = lessonProgressRepository
            .findByEnrollmentIdAndLessonId(enrollmentId, lessonId)
            .orElse(LessonProgress.builder()
                .enrollmentId(enrollmentId)
                .lessonId(lessonId)
                .startedAt(LocalDateTime.now())
                .build());

        ProgressStatus ps = ProgressStatus.valueOf(status.toUpperCase());
        progress.setStatus(ps);
        if (timeSpent != null) {
            progress.setTimeSpentSeconds(
                (progress.getTimeSpentSeconds() != null
                    ? progress.getTimeSpentSeconds() : 0) + timeSpent);
        }
        progress.setLastActivityAt(LocalDateTime.now());
        if (ps == ProgressStatus.COMPLETED && progress.getCompletedAt() == null) {
            progress.setCompletedAt(LocalDateTime.now());
        }
        return lessonProgressRepository.save(progress);
    }

    @Override
    public List<LessonProgress> getProgressByEnrollment(Long enrollmentId) {
        return lessonProgressRepository.findByEnrollmentId(enrollmentId);
    }

    @Override
    public long countCompletedLessons(Long enrollmentId) {
        return lessonProgressRepository
            .countByEnrollmentIdAndStatus(enrollmentId, ProgressStatus.COMPLETED);
    }

    @Override
    @Transactional
    public void deleteProgressByEnrollmentIds(List<Long> enrollmentIds) {
        lessonProgressRepository.deleteAllByEnrollmentIdIn(enrollmentIds);
    }

    @Override
    @Transactional
    public LessonProgress trackTimeOnly(Long enrollmentId, Long lessonId, Long timeSpent) {
        LessonProgress progress = lessonProgressRepository
            .findByEnrollmentIdAndLessonId(enrollmentId, lessonId)
            .orElse(LessonProgress.builder()
                .enrollmentId(enrollmentId)
                .lessonId(lessonId)
                .status(ProgressStatus.NOT_STARTED)
                .startedAt(LocalDateTime.now())
                .build());
        if (timeSpent != null) {
            progress.setTimeSpentSeconds(
                (progress.getTimeSpentSeconds() != null ? progress.getTimeSpentSeconds() : 0) + timeSpent);
        }
        progress.setLastActivityAt(LocalDateTime.now());
        return lessonProgressRepository.save(progress);
    }

    @Override
    public List<Lesson> searchLessonsByTitle(String title) {
        return lessonRepository.findByTitleContainingIgnoreCase(title);
    }

    @Override
    public List<LessonProgress> getProgressByEnrollmentIds(List<Long> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return Collections.emptyList();
        return lessonProgressRepository.findByEnrollmentIdIn(enrollmentIds);
    }

    @Override
    public List<Long> getActiveEnrollmentIdsSince(LocalDateTime since) {
        return lessonProgressRepository.findEnrollmentIdsWithActivitySince(since);
    }

    @Override
    @Transactional
    public void deleteAllLessonsByCourse(Long courseId) {
        List<Lesson> lessons = lessonRepository.findByCourseId(courseId);
        lessonRepository.deleteAll(lessons);
        log.info("[LessonService] Deleted {} lessons for course {}", lessons.size(), courseId);
    }
}
