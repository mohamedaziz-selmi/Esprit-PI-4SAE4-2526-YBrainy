package tn.esprit.tpfoyer.Controllers;

import tn.esprit.tpfoyer.Clients.LessonClient;
import tn.esprit.tpfoyer.Dto.AiReaderContextDTO;
import tn.esprit.tpfoyer.Dto.AiSearchRequestDTO;
import tn.esprit.tpfoyer.Dto.AiSearchResultDTO;
import tn.esprit.tpfoyer.Dto.CourseDetailResponseDTO;
import tn.esprit.tpfoyer.Dto.CourseRequestDTO;
import tn.esprit.tpfoyer.Dto.CourseResponseDTO;
import tn.esprit.tpfoyer.Dto.VerificationResponseDTO;
import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import tn.esprit.tpfoyer.Services.ICertificateService;
import tn.esprit.tpfoyer.Services.ICourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final ICourseService courseService;
    private final ICertificateService certificateService;
    private final LessonClient lessonClient;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${ai.talking-head.base-url}")
    private String talkingHeadBaseUrl;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseResponseDTO> createCourse(
            @RequestPart("course") @Valid CourseRequestDTO dto,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.createCourse(dto, thumbnail, certificate));
    }

    @GetMapping
    public ResponseEntity<Page<CourseResponseDTO>> getAllCourses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CourseCategory category,
            @RequestParam(required = false) CourseLevel level,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "10")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(
                courseService.getAllCourses(search, category, level, minPrice, maxPrice, isPublished, instructorId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailResponseDTO> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseResponseDTO> updateCourse(
            @PathVariable Long id,
            @RequestPart("course") @Valid CourseRequestDTO dto,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate,
            @RequestParam(required = false) Long requestingUserId,
            @RequestParam(required = false) String requestingRole) {

        return ResponseEntity.ok(courseService.updateCourse(id, dto, thumbnail, certificate, requestingUserId, requestingRole));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long id,
            @RequestParam(required = false) Long requestingUserId,
            @RequestParam(required = false) String requestingRole) {
        courseService.deleteCourse(id, requestingUserId, requestingRole);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<?> togglePublish(
            @PathVariable Long id,
            @RequestParam boolean publish,
            @RequestParam(required = false) Long requestingUserId,
            @RequestParam(required = false) String requestingRole) {
        // Authorization: STUDENT cannot publish or unpublish any course
        if ("STUDENT".equalsIgnoreCase(requestingRole)) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Students are not allowed to publish or unpublish courses"));
        }
        try {
            return ResponseEntity.ok(
                courseService.togglePublish(id, publish, requestingUserId, requestingRole));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search/ai")
    public ResponseEntity<?> aiSearch(
            @RequestBody AiSearchRequestDTO request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return ResponseEntity.badRequest().body("Query must not be blank");
        }
        AiSearchResultDTO result = courseService.aiSearch(request.getQuery(), page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{courseId}/certificate")
    public ResponseEntity<?> downloadCertificate(
            @PathVariable Long courseId,
            @RequestParam Long studentId) {
        try {
            String certId = certificateService.generateCertificate(courseId, studentId);
            String filePath = uploadDir + "/certificates/cert_" + studentId + "_" + courseId + ".pdf";
            File file = new File(filePath);
            if (!file.exists() || file.length() == 0) {
                return ResponseEntity.status(404).body("Certificate file not found");
            }
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"YBrainy-Certificate.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("not found")) return ResponseEntity.status(404).body(msg);
            if (msg.contains("not yet completed")) return ResponseEntity.status(403).body(msg);
            return ResponseEntity.status(500).body("Certificate generation failed: " + msg);
        }
    }

    @GetMapping("/verify/{certificateId}")
    public ResponseEntity<VerificationResponseDTO> verifyCertificate(
            @PathVariable String certificateId) {
        return ResponseEntity.ok(certificateService.verifyCertificate(certificateId));
    }

    // Used by Lesson Service via Feign to verify course exists
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> courseExists(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.existsById(id));
    }

    // GET /api/courses/stats/by-category
    @GetMapping("/stats/by-category")
    public ResponseEntity<?> getCourseStatsByCategory() {
        try {
            return ResponseEntity.ok(courseService.getCourseStatsByCategory());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new HashMap<>());
        }
    }

    @GetMapping("/{courseId}/reader-context")
    public ResponseEntity<AiReaderContextDTO> getCourseReaderContext(@PathVariable Long courseId) {
        CourseDetailResponseDTO course = courseService.getCourseById(courseId);
        String text = course.getDescription() != null && !course.getDescription().isBlank()
                ? course.getDescription()
                : course.getTitle();
        return ResponseEntity.ok(AiReaderContextDTO.builder()
                .sourceType("COURSE")
                .courseId(courseId)
                .lessonId(null)
                .title(course.getTitle())
                .text(text)
                .talkingHeadBaseUrl(talkingHeadBaseUrl)
                .speakerWavPath(null)
                .speakerSource("HARVARD_DEFAULT")
                .build());
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/reader-context")
    public ResponseEntity<AiReaderContextDTO> getLessonReaderContext(
            @PathVariable Long courseId, @PathVariable Long lessonId) {
        String title;
        String text;
        try {
            Map<String, Object> lesson = lessonClient.getLessonByCourse(courseId, lessonId);
            title = String.valueOf(lesson.getOrDefault("title", "Lesson"));
            Object content = lesson.get("content");
            Object desc = lesson.get("description");
            text = content != null && !String.valueOf(content).isBlank() ? String.valueOf(content)
                 : desc    != null && !String.valueOf(desc).isBlank()     ? String.valueOf(desc)
                 : title;
        } catch (Exception e) {
            CourseDetailResponseDTO course = courseService.getCourseById(courseId);
            title = course.getTitle();
            text = course.getDescription() != null ? course.getDescription() : title;
        }
        return ResponseEntity.ok(AiReaderContextDTO.builder()
                .sourceType("LESSON")
                .courseId(courseId)
                .lessonId(lessonId)
                .title(title)
                .text(text)
                .talkingHeadBaseUrl(talkingHeadBaseUrl)
                .speakerWavPath(null)
                .speakerSource("HARVARD_DEFAULT")
                .build());
    }
}
