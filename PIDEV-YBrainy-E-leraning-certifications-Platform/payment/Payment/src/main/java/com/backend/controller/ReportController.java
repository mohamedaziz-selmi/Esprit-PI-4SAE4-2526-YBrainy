package com.backend.controller;

import com.backend.entity.Report;
import com.backend.repository.ReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin("*")
public class ReportController {

    private final ReportRepository reportRepo;

    public ReportController(ReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    /* ─── LIST by type ─── */
    @GetMapping("/{type}")
    public List<Report> getByType(@PathVariable String type) {
        return reportRepo.findByTypeOrderByCreatedAtDesc(type);
    }

    /* ─── LIST by type + status ─── */
    @GetMapping("/{type}/status/{status}")
    public List<Report> getByTypeAndStatus(@PathVariable String type, @PathVariable String status) {
        return reportRepo.findByTypeAndStatusOrderByCreatedAtDesc(type, status);
    }

    /* ─── LIST by type + category ─── */
    @GetMapping("/{type}/category/{category}")
    public List<Report> getByTypeAndCategory(@PathVariable String type, @PathVariable String category) {
        return reportRepo.findByTypeAndCategoryOrderByCreatedAtDesc(type, category);
    }

    /* ─── STATS ─── */
    @GetMapping("/{type}/stats")
    public Map<String, Object> getStats(@PathVariable String type) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", reportRepo.countByType(type));
        stats.put("open", reportRepo.countByTypeAndStatus(type, "open"));
        stats.put("in_progress", reportRepo.countByTypeAndStatus(type, "in_progress"));
        stats.put("resolved", reportRepo.countByTypeAndStatus(type, "resolved"));
        stats.put("closed", reportRepo.countByTypeAndStatus(type, "closed"));
        stats.put("high", reportRepo.countByTypeAndPriority(type, "high"));
        stats.put("critical", reportRepo.countByTypeAndPriority(type, "critical"));
        return stats;
    }

    /* ─── GET single ─── */
    @GetMapping("/detail/{id}")
    public ResponseEntity<Report> getById(@PathVariable Long id) {
        return reportRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── CREATE ─── */
    @PostMapping
    public ResponseEntity<Report> create(@RequestBody Report report) {
        return ResponseEntity.ok(reportRepo.save(report));
    }

    /* ─── UPDATE ─── */
    @PutMapping("/{id}")
    public ResponseEntity<Report> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return reportRepo.findById(id).map(report -> {
            if (body.containsKey("title")) report.setTitle((String) body.get("title"));
            if (body.containsKey("description")) report.setDescription((String) body.get("description"));
            if (body.containsKey("category")) report.setCategory((String) body.get("category"));
            if (body.containsKey("priority")) report.setPriority((String) body.get("priority"));
            if (body.containsKey("status")) report.setStatus((String) body.get("status"));
            if (body.containsKey("subjectName")) report.setSubjectName((String) body.get("subjectName"));
            if (body.containsKey("subjectEmail")) report.setSubjectEmail((String) body.get("subjectEmail"));
            if (body.containsKey("submittedBy")) report.setSubmittedBy((String) body.get("submittedBy"));
            if (body.containsKey("courseName")) report.setCourseName((String) body.get("courseName"));
            if (body.containsKey("certificationName")) report.setCertificationName((String) body.get("certificationName"));
            return ResponseEntity.ok(reportRepo.save(report));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!reportRepo.existsById(id)) return ResponseEntity.notFound().build();
        reportRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

