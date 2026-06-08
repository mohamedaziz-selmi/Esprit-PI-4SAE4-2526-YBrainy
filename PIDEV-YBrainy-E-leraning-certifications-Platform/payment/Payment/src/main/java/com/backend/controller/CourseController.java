package com.backend.controller;

import com.backend.entity.Course;
import com.backend.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8092"}, allowCredentials = "true")
public class CourseController {

    private final CourseRepository courseRepo;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public CourseController(CourseRepository courseRepo) {
        this.courseRepo = courseRepo;
    }

    /* ─── LIST ─── */
    @GetMapping
    public List<Course> getAll() {
        return courseRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/category/{category}")
    public List<Course> getByCategory(@PathVariable String category) {
        return courseRepo.findByCategoryIgnoreCase(category);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getById(@PathVariable Long id) {
        return courseRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── CREATE with optional video thumbnail ─── */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Course> create(
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile thumbnailVideo) throws IOException {

        Course course = Course.builder()
                .title(title)
                .category(category)
                .description(description)
                .build();

        if (thumbnailVideo != null && !thumbnailVideo.isEmpty()) {
            String videoPath = saveFile(thumbnailVideo, "thumbnails");
            course.setThumbnailVideoPath(videoPath);
        }

        Course saved = courseRepo.save(course);
        return ResponseEntity.ok(saved);
    }

    /* ─── UPDATE ─── */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<Course> update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile thumbnailVideo) throws IOException {

        return courseRepo.findById(id).map(course -> {
            course.setTitle(title);
            course.setCategory(category);
            course.setDescription(description);

            if (thumbnailVideo != null && !thumbnailVideo.isEmpty()) {
                try {
                    String videoPath = saveFile(thumbnailVideo, "thumbnails");
                    course.setThumbnailVideoPath(videoPath);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload video", e);
                }
            }

            return ResponseEntity.ok(courseRepo.save(course));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!courseRepo.existsById(id)) return ResponseEntity.notFound().build();
        courseRepo.deleteById(id);
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

