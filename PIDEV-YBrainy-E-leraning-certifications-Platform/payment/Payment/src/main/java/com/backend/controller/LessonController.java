package com.backend.controller;

import com.backend.entity.Course;
import com.backend.entity.Lesson;
import com.backend.repository.CourseRepository;
import com.backend.repository.LessonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/lessons")
@CrossOrigin("*")
public class LessonController {

    private final LessonRepository lessonRepo;
    private final CourseRepository courseRepo;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public LessonController(LessonRepository lessonRepo, CourseRepository courseRepo) {
        this.lessonRepo = lessonRepo;
        this.courseRepo = courseRepo;
    }

    /* ─── LIST by course ─── */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<Lesson>> getByCourse(@PathVariable Long courseId) {
        if (!courseRepo.existsById(courseId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(lessonRepo.findByCourseIdOrderByOrderIndexAsc(courseId));
    }

    /* ─── GET single ─── */
    @GetMapping("/{id}")
    public ResponseEntity<Lesson> getById(@PathVariable Long id) {
        return lessonRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── CREATE with optional video ─── */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Lesson> create(
            @RequestParam Long courseId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String duration,
            @RequestParam(required = false) MultipartFile video) throws IOException {

        Optional<Course> courseOpt = courseRepo.findById(courseId);
        if (courseOpt.isEmpty()) return ResponseEntity.badRequest().build();

        int nextOrder = lessonRepo.countByCourseId(courseId);

        Lesson lesson = Lesson.builder()
                .title(title)
                .description(description)
                .duration(duration)
                .orderIndex(nextOrder)
                .course(courseOpt.get())
                .build();

        if (video != null && !video.isEmpty()) {
            String videoPath = saveFile(video, "lessons");
            lesson.setVideoPath(videoPath);
        }

        return ResponseEntity.ok(lessonRepo.save(lesson));
    }

    /* ─── UPDATE ─── */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Lesson> update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String duration,
            @RequestParam(required = false) MultipartFile video) throws IOException {

        return lessonRepo.findById(id).map(lesson -> {
            lesson.setTitle(title);
            lesson.setDescription(description);
            lesson.setDuration(duration);

            if (video != null && !video.isEmpty()) {
                try {
                    String videoPath = saveFile(video, "lessons");
                    lesson.setVideoPath(videoPath);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload video", e);
                }
            }

            return ResponseEntity.ok(lessonRepo.save(lesson));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── REORDER (drag & drop) ─── */
    @PutMapping("/reorder")
    public ResponseEntity<List<Lesson>> reorder(@RequestBody List<Map<String, Object>> order) {
        List<Lesson> updated = new ArrayList<>();
        for (Map<String, Object> item : order) {
            Long lessonId = Long.valueOf(item.get("id").toString());
            Integer newIndex = Integer.valueOf(item.get("orderIndex").toString());
            lessonRepo.findById(lessonId).ifPresent(lesson -> {
                lesson.setOrderIndex(newIndex);
                updated.add(lessonRepo.save(lesson));
            });
        }
        updated.sort(Comparator.comparingInt(Lesson::getOrderIndex));
        return ResponseEntity.ok(updated);
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!lessonRepo.existsById(id)) return ResponseEntity.notFound().build();
        lessonRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Helper: save uploaded file ─── */
    private String saveFile(MultipartFile file, String subfolder) throws IOException {
        String dir = uploadDir + "/" + subfolder;
        Path dirPath = Paths.get(dir);
        Files.createDirectories(dirPath);

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".mp4";
        String filename = UUID.randomUUID() + ext;

        Path target = dirPath.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + subfolder + "/" + filename;
    }
}

