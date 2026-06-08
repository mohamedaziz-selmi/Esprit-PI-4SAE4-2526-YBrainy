package com.backend.controller;

import com.backend.entity.Certification;
import com.backend.repository.CertificationRepository;
import com.backend.repository.CourseRepository;
import com.backend.repository.QuizRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/certifications")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8092"}, allowCredentials = "true")
public class CertificationController {

    private final CertificationRepository certRepo;
    private final CourseRepository courseRepo;
    private final QuizRepository quizRepo;

    public CertificationController(CertificationRepository certRepo, CourseRepository courseRepo, QuizRepository quizRepo) {
        this.certRepo = certRepo;
        this.courseRepo = courseRepo;
        this.quizRepo = quizRepo;
    }

    /* ─── LIST all ─── */
    @GetMapping
    public List<Certification> getAll() {
        return certRepo.findAllByOrderByCreatedAtDesc();
    }

    /* ─── LIST by status ─── */
    @GetMapping("/status/{status}")
    public List<Certification> getByStatus(@PathVariable String status) {
        return certRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    /* ─── LIST by category ─── */
    @GetMapping("/category/{category}")
    public List<Certification> getByCategory(@PathVariable String category) {
        return certRepo.findByCategoryIgnoreCaseOrderByCreatedAtDesc(category);
    }

    /* ─── STATS ─── */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", certRepo.count());
        stats.put("active", certRepo.countByStatus("active"));
        stats.put("draft", certRepo.countByStatus("draft"));
        stats.put("inactive", certRepo.countByStatus("inactive"));
        return stats;
    }

    /* ─── GET single ─── */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return certRepo.findById(id).map(cert -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", cert.getId());
            result.put("title", cert.getTitle());
            result.put("description", cert.getDescription());
            result.put("issuedBy", cert.getIssuedBy());
            result.put("category", cert.getCategory());
            result.put("level", cert.getLevel());
            result.put("duration", cert.getDuration());
            result.put("prerequisites", cert.getPrerequisites());
            result.put("badgeImageUrl", cert.getBadgeImageUrl());
            result.put("passingScore", cert.getPassingScore());
            result.put("earnedCount", cert.getEarnedCount());
            result.put("status", cert.getStatus());
            result.put("courseId", cert.getCourseId());
            result.put("courseTitle", cert.getCourseTitle());
            result.put("quizCount", quizRepo.countByCertification_Id(cert.getId()));
            result.put("createdAt", cert.getCreatedAt());
            result.put("updatedAt", cert.getUpdatedAt());
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── CREATE ─── */
    @PostMapping
    public ResponseEntity<Certification> create(@RequestBody Map<String, Object> body) {
        Certification cert = Certification.builder()
                .title((String) body.get("title"))
                .description((String) body.get("description"))
                .issuedBy((String) body.get("issuedBy"))
                .category((String) body.get("category"))
                .level((String) body.get("level"))
                .duration((String) body.get("duration"))
                .prerequisites((String) body.get("prerequisites"))
                .badgeImageUrl((String) body.get("badgeImageUrl"))
                .status((String) body.getOrDefault("status", "active"))
                .build();

        if (body.get("passingScore") != null)
            cert.setPassingScore(Integer.valueOf(body.get("passingScore").toString()));

        if (body.get("courseId") != null) {
            Long courseId = Long.valueOf(body.get("courseId").toString());
            courseRepo.findById(courseId).ifPresent(cert::setCourse);
        }

        return ResponseEntity.ok(certRepo.save(cert));
    }

    /* ─── UPDATE ─── */
    @PutMapping("/{id}")
    public ResponseEntity<Certification> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return certRepo.findById(id).map(cert -> {
            if (body.containsKey("title")) cert.setTitle((String) body.get("title"));
            if (body.containsKey("description")) cert.setDescription((String) body.get("description"));
            if (body.containsKey("issuedBy")) cert.setIssuedBy((String) body.get("issuedBy"));
            if (body.containsKey("category")) cert.setCategory((String) body.get("category"));
            if (body.containsKey("level")) cert.setLevel((String) body.get("level"));
            if (body.containsKey("duration")) cert.setDuration((String) body.get("duration"));
            if (body.containsKey("prerequisites")) cert.setPrerequisites((String) body.get("prerequisites"));
            if (body.containsKey("badgeImageUrl")) cert.setBadgeImageUrl((String) body.get("badgeImageUrl"));
            if (body.containsKey("status")) cert.setStatus((String) body.get("status"));
            if (body.containsKey("passingScore"))
                cert.setPassingScore(Integer.valueOf(body.get("passingScore").toString()));
            if (body.containsKey("courseId")) {
                if (body.get("courseId") != null) {
                    Long courseId = Long.valueOf(body.get("courseId").toString());
                    courseRepo.findById(courseId).ifPresent(cert::setCourse);
                } else {
                    cert.setCourse(null);
                }
            }
            return ResponseEntity.ok(certRepo.save(cert));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!certRepo.existsById(id)) return ResponseEntity.notFound().build();
        certRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

