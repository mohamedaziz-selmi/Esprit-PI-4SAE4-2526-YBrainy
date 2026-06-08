package tn.esprit.lessonservice.services;

import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonProgress;
import java.time.LocalDateTime;
import java.util.List;

public interface ILessonService {
    List<Lesson> getLessonsByCourse(Long courseId);
    Lesson getLessonById(Long id);
    Lesson getLessonByIdAndCourse(Long id, Long courseId);
    Lesson createLesson(Long courseId, Lesson lesson);
    Lesson updateLesson(Long courseId, Long lessonId, Lesson lesson);
    void deleteLesson(Long courseId, Long lessonId);
    long countLessonsByCourse(Long courseId);
    List<Lesson> searchLessonsByTitle(String title);

    // Progress
    LessonProgress trackProgress(Long enrollmentId, Long lessonId, String status, Long timeSpent);
    LessonProgress trackTimeOnly(Long enrollmentId, Long lessonId, Long timeSpent);
    List<LessonProgress> getProgressByEnrollment(Long enrollmentId);
    List<LessonProgress> getProgressByEnrollmentIds(List<Long> enrollmentIds);
    List<Long> getActiveEnrollmentIdsSince(LocalDateTime since);
    long countCompletedLessons(Long enrollmentId);
    void deleteProgressByEnrollmentIds(List<Long> enrollmentIds);
    void deleteAllLessonsByCourse(Long courseId);
}
