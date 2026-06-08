package com.backend.controller;

import com.backend.entity.CvSubmission;
import com.backend.repository.CvSubmissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/cvs")
@CrossOrigin("*")
public class CvSubmissionController {

    private final CvSubmissionRepository cvRepo;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public CvSubmissionController(CvSubmissionRepository cvRepo) {
        this.cvRepo = cvRepo;
    }

    /* ─── LIST all CVs ─── */
    @GetMapping
    public List<CvSubmission> getAll() {
        return cvRepo.findAllByOrderBySubmittedAtDesc();
    }

    /* ─── LIST by status ─── */
    @GetMapping("/status/{status}")
    public List<CvSubmission> getByStatus(@PathVariable String status) {
        return cvRepo.findByStatusOrderBySubmittedAtDesc(status);
    }

    /* ─── STATS ─── */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", cvRepo.count());
        stats.put("pending", cvRepo.countByStatus("pending"));
        stats.put("reviewed", cvRepo.countByStatus("reviewed"));
        stats.put("shortlisted", cvRepo.countByStatus("shortlisted"));
        stats.put("accepted", cvRepo.countByStatus("accepted"));
        stats.put("rejected", cvRepo.countByStatus("rejected"));
        return stats;
    }

    /* ─── GET single ─── */
    @GetMapping("/{id}")
    public ResponseEntity<CvSubmission> getById(@PathVariable Long id) {
        return cvRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── SUBMIT CV (with file upload) ─── */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CvSubmission> submit(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String coverLetter,
            @RequestParam(required = false) String educationLevel,
            @RequestParam(required = false) Integer yearsOfExperience,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) MultipartFile cvFile) throws IOException {

        CvSubmission cv = CvSubmission.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .position(position)
                .courseName(courseName)
                .coverLetter(coverLetter)
                .educationLevel(educationLevel)
                .yearsOfExperience(yearsOfExperience)
                .skills(skills)
                .status("pending")
                .build();

        if (cvFile != null && !cvFile.isEmpty()) {
            String filePath = saveFile(cvFile);
            cv.setCvFilePath(filePath);
            cv.setCvFileName(cvFile.getOriginalFilename());
        }

        return ResponseEntity.ok(cvRepo.save(cv));
    }

    /* ─── UPDATE status / reviewer notes ─── */
    @PutMapping("/{id}")
    public ResponseEntity<CvSubmission> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return cvRepo.findById(id).map(cv -> {
            if (body.containsKey("status")) {
                cv.setStatus((String) body.get("status"));
                cv.setReviewedAt(LocalDateTime.now());
            }
            if (body.containsKey("reviewerNotes")) cv.setReviewerNotes((String) body.get("reviewerNotes"));
            if (body.containsKey("fullName")) cv.setFullName((String) body.get("fullName"));
            if (body.containsKey("email")) cv.setEmail((String) body.get("email"));
            if (body.containsKey("phone")) cv.setPhone((String) body.get("phone"));
            if (body.containsKey("position")) cv.setPosition((String) body.get("position"));
            if (body.containsKey("courseName")) cv.setCourseName((String) body.get("courseName"));
            if (body.containsKey("educationLevel")) cv.setEducationLevel((String) body.get("educationLevel"));
            if (body.containsKey("skills")) cv.setSkills((String) body.get("skills"));
            return ResponseEntity.ok(cvRepo.save(cv));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!cvRepo.existsById(id)) return ResponseEntity.notFound().build();
        cvRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ─── Helper: save uploaded file ─── */
    private String saveFile(MultipartFile file) throws IOException {
        String dir = uploadDir + "/cvs";
        Path dirPath = Paths.get(dir);
        Files.createDirectories(dirPath);

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".pdf";
        String filename = UUID.randomUUID() + ext;

        Path target = dirPath.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/cvs/" + filename;
    }
}

