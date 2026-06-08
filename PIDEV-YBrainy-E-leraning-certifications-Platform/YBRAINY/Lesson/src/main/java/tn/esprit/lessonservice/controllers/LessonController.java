package tn.esprit.lessonservice.controllers;

import tn.esprit.lessonservice.entities.*;
import tn.esprit.lessonservice.services.ILessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final ILessonService lessonService;

    // GET lessons by course
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<Lesson>> getLessonsByCourse(
            @PathVariable Long courseId) {
        return ResponseEntity.ok(lessonService.getLessonsByCourse(courseId));
    }

    // GET single lesson
    @GetMapping("/{id}")
    public ResponseEntity<Lesson> getLesson(@PathVariable Long id) {
        return ResponseEntity.ok(lessonService.getLessonById(id));
    }

    // GET lesson by id and course
    @GetMapping("/course/{courseId}/lesson/{lessonId}")
    public ResponseEntity<Lesson> getLessonByCourse(
            @PathVariable Long courseId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(
            lessonService.getLessonByIdAndCourse(lessonId, courseId));
    }

    // GET lesson count for course — used by other services via Feign
    @GetMapping("/course/{courseId}/count")
    public ResponseEntity<Long> getLessonCount(@PathVariable Long courseId) {
        return ResponseEntity.ok(lessonService.countLessonsByCourse(courseId));
    }

    // POST create lesson
    @PostMapping("/course/{courseId}")
    public ResponseEntity<Lesson> createLesson(
            @PathVariable Long courseId,
            @RequestBody Lesson lesson) {
        return ResponseEntity.ok(lessonService.createLesson(courseId, lesson));
    }

    // PUT update lesson
    @PutMapping("/course/{courseId}/lesson/{lessonId}")
    public ResponseEntity<Lesson> updateLesson(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestBody Lesson lesson) {
        return ResponseEntity.ok(
            lessonService.updateLesson(courseId, lessonId, lesson));
    }

    // Delete all lessons for a course — called by Course Service via Feign
    @DeleteMapping("/course/{courseId}/all")
    public ResponseEntity<Void> deleteAllLessonsByCourse(
            @PathVariable Long courseId) {
        lessonService.deleteAllLessonsByCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    // DELETE lesson
    @DeleteMapping("/course/{courseId}/lesson/{lessonId}")
    public ResponseEntity<Void> deleteLesson(
            @PathVariable Long courseId,
            @PathVariable Long lessonId) {
        lessonService.deleteLesson(courseId, lessonId);
        return ResponseEntity.noContent().build();
    }

    // GET lessons by title search — used by Course Service AI search via Feign
    @GetMapping("/search")
    public ResponseEntity<List<Lesson>> searchByTitle(@RequestParam String title) {
        return ResponseEntity.ok(lessonService.searchLessonsByTitle(title));
    }

    // === PROGRESS ENDPOINTS ===

    @PostMapping("/progress")
    public ResponseEntity<LessonProgress> trackProgress(
            @RequestParam Long enrollmentId,
            @RequestParam Long lessonId,
            @RequestParam String status,
            @RequestParam(required = false) Long timeSpent) {
        return ResponseEntity.ok(
            lessonService.trackProgress(enrollmentId, lessonId, status, timeSpent));
    }

    @GetMapping("/progress/enrollment/{enrollmentId}")
    public ResponseEntity<List<LessonProgress>> getProgress(
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(
            lessonService.getProgressByEnrollment(enrollmentId));
    }

    @GetMapping("/progress/enrollment/{enrollmentId}/completed-count")
    public ResponseEntity<Long> getCompletedCount(
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(
            lessonService.countCompletedLessons(enrollmentId));
    }

    @PostMapping("/progress/time")
    public ResponseEntity<LessonProgress> trackTimeOnly(
            @RequestParam Long enrollmentId,
            @RequestParam Long lessonId,
            @RequestParam Long timeSpent) {
        return ResponseEntity.ok(lessonService.trackTimeOnly(enrollmentId, lessonId, timeSpent));
    }

    @PostMapping("/progress/by-enrollments")
    public ResponseEntity<List<LessonProgress>> getProgressByEnrollmentIds(
            @RequestBody List<Long> enrollmentIds) {
        return ResponseEntity.ok(lessonService.getProgressByEnrollmentIds(enrollmentIds));
    }

    @GetMapping("/progress/active-since")
    public ResponseEntity<List<Long>> getActiveEnrollmentIdsSince(
            @RequestParam String since) {
        return ResponseEntity.ok(
            lessonService.getActiveEnrollmentIdsSince(LocalDateTime.parse(since)));
    }

    @DeleteMapping("/progress/enrollments")
    public ResponseEntity<Void> deleteProgressByEnrollments(
            @RequestBody List<Long> enrollmentIds) {
        lessonService.deleteProgressByEnrollmentIds(enrollmentIds);
        return ResponseEntity.noContent().build();
    }
}
