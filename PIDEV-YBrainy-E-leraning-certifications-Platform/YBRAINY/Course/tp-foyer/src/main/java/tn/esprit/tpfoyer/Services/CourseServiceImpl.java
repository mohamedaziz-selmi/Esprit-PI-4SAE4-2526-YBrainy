package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.AiSearchIntentDTO;
import tn.esprit.tpfoyer.Dto.AiSearchResultDTO;
import tn.esprit.tpfoyer.Dto.CourseAnalyticsDTO;
import tn.esprit.tpfoyer.Dto.CourseDetailResponseDTO;
import tn.esprit.tpfoyer.Dto.CourseRequestDTO;
import tn.esprit.tpfoyer.Dto.CourseResponseDTO;
import tn.esprit.tpfoyer.Dto.LessonContentResponseDTO;
import tn.esprit.tpfoyer.Dto.LessonResponseDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import tn.esprit.tpfoyer.Entities.enums.LessonType;
import tn.esprit.tpfoyer.Exception.ResourceNotFoundException;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.CourseReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.tpfoyer.Clients.LessonClient;
import tn.esprit.tpfoyer.Config.RabbitMQConfig;
import tn.esprit.tpfoyer.Events.CourseDeletedEvent;
import tn.esprit.tpfoyer.Repositories.CourseSpecification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CourseServiceImpl implements ICourseService {

    private final CourseRepository courseRepository;
    private final IFileStorageService fileStorageService;
    private final IAiSearchService aiSearchService;
    private final EnrollmentClient enrollmentClient;
    private final CourseReviewRepository courseReviewRepository;
    private final LessonClient lessonClient;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Override
    public CourseResponseDTO createCourse(CourseRequestDTO dto, MultipartFile thumbnail, MultipartFile certificate) {
        Course course = Course.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .category(dto.getCategory())
                .level(dto.getLevel())
                .approximateDurationMinutes(dto.getApproximateDurationMinutes())
                .isPublished(dto.getIsPublished() != null ? dto.getIsPublished() : false)
                .offersCertificate(dto.getOffersCertificate() != null ? dto.getOffersCertificate() : false)
                .instructorId(dto.getInstructorId())
                .build();

        if (thumbnail != null && !thumbnail.isEmpty()) {
            course.setThumbnailUrl(fileStorageService.storeThumbnail(thumbnail));
        }

        if (Boolean.TRUE.equals(course.getOffersCertificate())) {
            if (certificate == null || certificate.isEmpty()) {
                throw new IllegalArgumentException("Certificate file is required when offersCertificate is true");
            }
            course.setCertificatePath(fileStorageService.storeCertificate(certificate));
        }

        return toResponseDTO(courseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponseDTO> getAllCourses(
            String search,
            CourseCategory category,
            CourseLevel level,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean isPublished,
            Long instructorId,
            Pageable pageable) {

        Specification<Course> spec = Specification
                .where(CourseSpecification.searchByKeyword(search))
                .and(CourseSpecification.hasCategory(category))
                .and(CourseSpecification.hasLevel(level))
                .and(CourseSpecification.hasPriceGreaterThanOrEqual(minPrice))
                .and(CourseSpecification.hasPriceLessThanOrEqual(maxPrice))
                .and(CourseSpecification.isPublished(isPublished))
                .and(CourseSpecification.hasInstructorId(instructorId));

        Page<Course> coursePage = courseRepository.findAll(spec, pageable);
        
        // Batch fetch lesson counts to avoid N+1 query problem
        List<Long> courseIds = coursePage.getContent().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        
        Map<Long, Integer> lessonCountMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (Long cid : courseIds) {
                try {
                    Long count = lessonClient.getLessonCount(cid);
                    lessonCountMap.put(cid, count != null ? count.intValue() : 0);
                } catch (Exception ignored) {
                    lessonCountMap.put(cid, 0);
                }
            }
        }
        
        return coursePage.map(course -> toResponseDTO(course, lessonCountMap));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponseDTO getCourseById(Long id) {
        return mapToDetailDTO(findOrThrow(id));
    }

    @Override
    public CourseResponseDTO updateCourse(Long id, CourseRequestDTO dto, MultipartFile thumbnail, MultipartFile certificate, Long requestingUserId, String requestingRole) {
        Course course = findOrThrow(id);
        validateOwnership(course, requestingUserId, requestingRole);

        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course.setPrice(dto.getPrice());
        course.setCategory(dto.getCategory());
        course.setLevel(dto.getLevel());
        course.setApproximateDurationMinutes(dto.getApproximateDurationMinutes());
        course.setInstructorId(dto.getInstructorId());
        if (dto.getIsPublished() != null) {
            course.setIsPublished(dto.getIsPublished());
        }

        if (dto.getOffersCertificate() != null) {
            course.setOffersCertificate(dto.getOffersCertificate());
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            fileStorageService.deleteFile(course.getThumbnailUrl());
            course.setThumbnailUrl(fileStorageService.storeThumbnail(thumbnail));
        }

        if (Boolean.TRUE.equals(course.getOffersCertificate())) {
            if (certificate != null && !certificate.isEmpty()) {
                fileStorageService.deleteFile(course.getCertificatePath());
                course.setCertificatePath(fileStorageService.storeCertificate(certificate));
            } else if (course.getCertificatePath() == null || course.getCertificatePath().isBlank()) {
                throw new IllegalArgumentException("Certificate file is required when offersCertificate is true");
            }
        } else {
            fileStorageService.deleteFile(course.getCertificatePath());
            course.setCertificatePath(null);
        }

        return toResponseDTO(courseRepository.save(course));
    }

    @Override
    public void deleteCourse(Long id, Long requestingUserId, String requestingRole) {
        Course course = findOrThrow(id);
        validateOwnership(course, requestingUserId, requestingRole);

        // Delete LessonProgress records for all Enrollments of this course (via Lesson Service)
        try {
            List<Map<String, Object>> courseEnrollments = enrollmentClient.getEnrollmentsByCourse(id);
            List<Long> enrollmentIds = courseEnrollments.stream()
                .filter(e -> e.get("id") instanceof Number)
                .map(e -> ((Number) e.get("id")).longValue())
                .collect(Collectors.toList());
            if (!enrollmentIds.isEmpty()) {
                lessonClient.deleteProgressByEnrollments(enrollmentIds);
            }
        } catch (Exception e) {
            log.warn("Could not delete lesson progress for course {}: {}", id, e.getMessage());
        }

        // Delete all Enrollments for this course (via Enrollment Service)
        try {
            enrollmentClient.deleteEnrollmentsByCourse(id);
        } catch (Exception e) {
            log.warn("Could not delete enrollments for course {}: {}", id, e.getMessage());
        }

        // Delete all CourseReviews for this course
        courseReviewRepository.deleteAllByCourseId(id);

        // Delete course files
        fileStorageService.deleteFile(course.getThumbnailUrl());
        fileStorageService.deleteFile(course.getCertificatePath());

        // Delete all lessons for this course (via Lesson Service)
        // This MUST succeed before we can delete the course (FK constraint)
        try {
            lessonClient.deleteAllLessonsByCourse(id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot delete course: failed to delete lessons for course " + id + ": " + e.getMessage(), e);
        }

        // Publish course.deleted event — Quiz Service will
        // handle quiz deletion asynchronously via RabbitMQ
        try {
            CourseDeletedEvent event = CourseDeletedEvent.builder()
                .courseId(id)
                .courseTitle(course.getTitle())
                .build();
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.COURSE_EXCHANGE,
                RabbitMQConfig.COURSE_DELETED_KEY,
                event);
            log.info("[Course] Published course.deleted event for courseId={}", id);
        } catch (Exception e) {
            log.warn("[Course] Could not publish course.deleted event: {}", e.getMessage());
        }

        courseRepository.delete(course);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseAnalyticsDTO getCourseAnalytics(Long courseId) {
        Course course = findOrThrow(courseId);

        // Get lesson statistics via Lesson Service
        List<Map<String, Object>> lessons = Collections.emptyList();
        try {
            lessons = lessonClient.getLessonsByCourse(courseId);
        } catch (Exception e) {
            log.warn("Could not fetch lessons for course {}: {}", courseId, e.getMessage());
        }
        int totalLessons = lessons.size();

        // Calculate total video minutes and material counts
        long totalVideoMinutes = 0;
        int totalPdfMaterials = 0;
        int totalImageMaterials = 0;

        for (Map<String, Object> lesson : lessons) {
            if (lesson.get("durationMinutes") instanceof Number n) {
                totalVideoMinutes += n.longValue();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) lesson.getOrDefault("contents", Collections.emptyList());
            for (Map<String, Object> content : contents) {
                String ctype = (String) content.get("type");
                if ("PDF".equals(ctype)) totalPdfMaterials++;
                else if ("IMAGE".equals(ctype)) totalImageMaterials++;
            }
        }
        
        // Real enrollment metrics (via Enrollment Service Feign client)
        int totalEnrollments = 0;
        int completedStudents = 0;
        double averageProgress = 0.0;
        int activeStudents7 = 0;
        int activeStudents30 = 0;
        double totalRevenue = 0.0;
        double monthlyRevenue = 0.0;
        List<Map<String, Object>> enrollments = Collections.emptyList();

        try {
            enrollments = enrollmentClient.getEnrollmentsByCourse(courseId);
            totalEnrollments = enrollments.size();
            completedStudents = (int) enrollments.stream()
                    .filter(e -> "COMPLETED".equals(e.get("status")))
                    .count();
            averageProgress = enrollments.isEmpty() ? 0.0 : enrollments.stream()
                    .mapToDouble(e -> e.get("completionPercentage") instanceof Number n ? n.doubleValue() : 0.0)
                    .average()
                    .orElse(0.0);

            // Active students based on real lesson activity (via Lesson Service)
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Set<Long> enrollmentIds = enrollments.stream()
                    .filter(e -> e.get("id") instanceof Number)
                    .map(e -> ((Number) e.get("id")).longValue())
                    .collect(Collectors.toSet());
            try {
                List<Long> recentIds7 = lessonClient.getActiveEnrollmentIdsSince(sevenDaysAgo.toString());
                List<Long> recentIds30 = lessonClient.getActiveEnrollmentIdsSince(thirtyDaysAgo.toString());
                activeStudents7 = (int) recentIds7.stream().filter(enrollmentIds::contains).count();
                activeStudents30 = (int) recentIds30.stream().filter(enrollmentIds::contains).count();
            } catch (Exception e) {
                log.warn("Could not fetch active enrollment IDs: {}", e.getMessage());
            }

            totalRevenue = enrollments.stream()
                    .filter(e -> e.get("paymentIntentId") != null)
                    .mapToDouble(e -> course.getPrice() != null ? course.getPrice().doubleValue() : 0.0)
                    .sum();
            LocalDateTime firstOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            monthlyRevenue = enrollments.stream()
                    .filter(e -> e.get("paymentIntentId") != null)
                    .filter(e -> {
                        Object ed = e.get("enrollmentDate");
                        if (ed instanceof String s) {
                            try { return LocalDateTime.parse(s).isAfter(firstOfMonth); } catch (Exception ignored2) {}
                        }
                        return false;
                    })
                    .mapToDouble(e -> course.getPrice() != null ? course.getPrice().doubleValue() : 0.0)
                    .sum();
        } catch (Exception e) {
            log.warn("Enrollment data unavailable for course {}: {}", courseId, e.getMessage());
        }

        // Calculate total time spent across all lesson progress records for this course (via Lesson Service)
        long totalTimeSpentSeconds = 0L;
        String totalTimeSpentFormatted = "0m";
        try {
            List<Long> enrollmentIdList = enrollments.stream()
                    .filter(e -> e.get("id") instanceof Number)
                    .map(e -> ((Number) e.get("id")).longValue())
                    .collect(Collectors.toList());
            if (!enrollmentIdList.isEmpty()) {
                List<Map<String, Object>> allProgress = lessonClient.getProgressByEnrollmentIds(enrollmentIdList);
                totalTimeSpentSeconds = allProgress.stream()
                        .mapToLong(lp -> lp.get("timeSpentSeconds") instanceof Number n ? n.longValue() : 0)
                        .sum();
                long hours = totalTimeSpentSeconds / 3600;
                long minutes = (totalTimeSpentSeconds % 3600) / 60;
                totalTimeSpentFormatted = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
            }
        } catch (Exception ignored) {}

        // Build analytics DTO
        return CourseAnalyticsDTO.builder()
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .totalEnrollments(totalEnrollments)
                .activeStudentsLast7Days(activeStudents7)
                .activeStudentsLast30Days(activeStudents30)
                .totalLessons(totalLessons)
                .averageProgressPercentage(averageProgress)
                .completedStudents(completedStudents)
                .averageRating(course.getRating() != null ? course.getRating().doubleValue() : 0.0)
                .totalRatings(course.getRatingCount() != null ? course.getRatingCount() : 0)
                .totalReviews((int) courseReviewRepository.countByCourseId(courseId))
                .totalVideoMinutes(totalVideoMinutes)
                .totalPdfMaterials(totalPdfMaterials)
                .totalImageMaterials(totalImageMaterials)
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .totalTimeSpentSeconds(totalTimeSpentSeconds)
                .totalTimeSpentFormatted(totalTimeSpentFormatted)
                .lastUpdated(course.getUpdatedAt() != null ? course.getUpdatedAt().toString() : null)
                .build();
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
    }

    private CourseResponseDTO toResponseDTO(Course course) {
        int lessonCount = 0;
        try {
            Long count = lessonClient.getLessonCount(course.getId());
            if (count != null) lessonCount = count.intValue();
        } catch (Exception ignored) {}
        return CourseResponseDTO.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .price(course.getPrice())
                .rating(course.getRating())
                .ratingCount(course.getRatingCount())
                .category(course.getCategory())
                .level(course.getLevel())
                .approximateDurationMinutes(course.getApproximateDurationMinutes())
                .isPublished(course.getIsPublished())
                .offersCertificate(course.getOffersCertificate())
                .lessonCount(lessonCount)
                .instructorId(course.getInstructorId())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }

    /**
     * Overloaded method that uses pre-fetched lesson counts to avoid N+1 queries.
     * Used when mapping a list of courses.
     */
    private CourseResponseDTO toResponseDTO(Course course, Map<Long, Integer> lessonCountMap) {
        int lessonCount = lessonCountMap.getOrDefault(course.getId(), 0);
        return CourseResponseDTO.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .price(course.getPrice())
                .rating(course.getRating())
                .ratingCount(course.getRatingCount())
                .category(course.getCategory())
                .level(course.getLevel())
                .approximateDurationMinutes(course.getApproximateDurationMinutes())
                .isPublished(course.getIsPublished())
                .offersCertificate(course.getOffersCertificate())
                .lessonCount(lessonCount)
                .instructorId(course.getInstructorId())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }

    private LessonResponseDTO mapLessonFromFeign(Map<String, Object> l) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawContents = (List<Map<String, Object>>) l.getOrDefault("contents", Collections.emptyList());
        List<LessonContentResponseDTO> contents = rawContents.stream()
                .map(c -> LessonContentResponseDTO.builder()
                        .id(c.get("id") instanceof Number n ? n.longValue() : null)
                        .type(c.get("type") != null ? LessonType.valueOf((String) c.get("type")) : null)
                        .contentUrl((String) c.get("contentUrl"))
                        .build())
                .collect(Collectors.toList());
        return LessonResponseDTO.builder()
                .id(l.get("id") instanceof Number n ? n.longValue() : null)
                .title((String) l.get("title"))
                .description((String) l.get("description"))
                .type(l.get("type") != null ? LessonType.valueOf((String) l.get("type")) : null)
                .contentUrl((String) l.get("contentUrl"))
                .contents(contents)
                .orderIndex(l.get("orderIndex") instanceof Number n ? n.intValue() : null)
                .durationMinutes(l.get("durationMinutes") instanceof Number n ? n.intValue() : null)
                .courseId(l.get("courseId") instanceof Number n ? n.longValue() : null)
                .build();
    }

    private CourseDetailResponseDTO mapToDetailDTO(Course course) {
        CourseResponseDTO base = toResponseDTO(course);

        List<LessonResponseDTO> lessons = Collections.emptyList();
        try {
            lessons = lessonClient.getLessonsByCourse(course.getId())
                    .stream()
                    .map(this::mapLessonFromFeign)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not fetch lessons for course {}: {}", course.getId(), e.getMessage());
        }
        int lessonCount = lessons.size();
        return CourseDetailResponseDTO.builder()
                .id(base.getId())
                .title(base.getTitle())
                .description(base.getDescription())
                .thumbnailUrl(base.getThumbnailUrl())
                .price(base.getPrice())
                .rating(base.getRating())
                .ratingCount(base.getRatingCount())
                .category(base.getCategory())
                .level(base.getLevel())
                .approximateDurationMinutes(base.getApproximateDurationMinutes())
                .isPublished(base.getIsPublished())
                .offersCertificate(base.getOffersCertificate())
                .lessonCount(lessonCount)
                .instructorId(base.getInstructorId())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .lessons(lessons)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AiSearchResultDTO aiSearch(String query, int page, int size) {
        AiSearchIntentDTO intent = aiSearchService.extractSearchIntent(query);

        // fall back to original query if keywords blank
        String keywords = (intent.getKeywords() != null && !intent.getKeywords().isBlank())
                ? intent.getKeywords()
                : query;

        CourseCategory category = null;
        if (intent.getCategory() != null && !intent.getCategory().equalsIgnoreCase("null")) {
            try {
                category = CourseCategory.valueOf(intent.getCategory().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        CourseLevel level = null;
        if (intent.getLevel() != null && !intent.getLevel().equalsIgnoreCase("null")) {
            try {
                level = CourseLevel.valueOf(intent.getLevel().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        log.info("[AI Search] query='{}' → category={} level={} keywords='{}'",
                query, category, level, keywords);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // STEP 1 — Try with all filters (keywords + category + level)
        Page<CourseResponseDTO> results = getAllCourses(
                keywords, category, level, null, null, null, null, pageable);
        log.info("[AI Search] STEP1 results: {}", results.getTotalElements());

        // STEP 2 — If empty and level was set, try without level
        if (results.isEmpty() && level != null) {
            results = getAllCourses(
                    keywords, category, null, null, null, null, null, pageable);
            log.info("[AI Search] STEP2 results: {}", results.getTotalElements());
        }

        // STEP 3 — If still empty and category was set, try keyword only (no category/level)
        if (results.isEmpty() && category != null) {
            results = getAllCourses(
                    keywords, null, null, null, null, null, null, pageable);
            log.info("[AI Search] STEP3 results: {}", results.getTotalElements());
        }

        // STEP 4 — If still empty and category was set, try category only (no keyword)
        if (results.isEmpty() && category != null) {
            results = getAllCourses(
                    null, category, null, null, null, null, null, pageable);
            log.info("[AI Search] STEP4 (category-only) results: {}", results.getTotalElements());
        }

        // STEP 5 — Last resort: raw query with no filters
        if (results.isEmpty()) {
            results = getAllCourses(
                    query, null, null, null, null, null, null, pageable);
            log.info("[AI Search] STEP5 results: {}", results.getTotalElements());
        }

        log.info("[AI Search] found {} candidates", results.getTotalElements());

        // ── Build mutable list for merging ───────────────────────────────────
        List<CourseResponseDTO> merged = new ArrayList<>(results.getContent());
        Set<Long> seenIds = merged.stream()
                .map(CourseResponseDTO::getId)
                .collect(Collectors.toSet());

        // FALLBACK ENRICHMENT — If < 3 results and a category was detected,
        // pad with category-only courses so the page is never nearly empty.
        if (merged.size() < 3 && category != null) {
            getAllCourses(null, category, null, null, null, null, null, pageable)
                    .getContent().stream()
                    .filter(c -> !seenIds.contains(c.getId()))
                    .forEach(c -> { merged.add(c); seenIds.add(c.getId()); });
            log.info("[AI Search] After category-only enrichment: {} candidates", merged.size());
        }

        // LESSON-TITLE SEARCH — find courses whose lesson titles match any keyword term
        // and merge them in (up to the requested page size).
        String[] terms = keywords.split("[,\\s]+");
        for (String term : terms) {
            String t = term.trim();
            if (t.isBlank() || merged.size() >= size) continue;
            try {
                List<Long> courseIdsFromLessons = lessonClient.searchLessonsByTitle(t)
                        .stream()
                        .map(l -> l.get("courseId") instanceof Number n ? n.longValue() : null)
                        .filter(id -> id != null && !seenIds.contains(id))
                        .distinct()
                        .collect(Collectors.toList());
                for (Long courseId : courseIdsFromLessons) {
                    if (merged.size() >= size) break;
                    courseRepository.findById(courseId).ifPresent(course -> {
                        merged.add(toResponseDTO(course));
                        seenIds.add(course.getId());
                    });
                }
            } catch (Exception e) {
                log.warn("Lesson title search failed for term '{}': {}", t, e.getMessage());
            }
        }
        log.info("[AI Search] Final result count after lesson-title merge: {}", merged.size());

        // Cap at requested page size and wrap in a Page
        List<CourseResponseDTO> capped = merged.stream().limit(size).collect(Collectors.toList());
        Page<CourseResponseDTO> finalPage = new PageImpl<>(
                capped,
                pageable,
                Math.max(results.getTotalElements(), capped.size())
        );

        return new AiSearchResultDTO(
                intent.getExplanation(),
                keywords,
                intent.getCategory(),
                intent.getLevel(),
                finalPage
        );
    }

    @Override
    public Map<String, Object> togglePublish(Long id, boolean publish, Long requestingUserId, String requestingRole) {
        Course course = findOrThrow(id);
        if ("INSTRUCTOR".equals(requestingRole) && requestingUserId != null) {
            if (course.getInstructorId() != null && !course.getInstructorId().equals(requestingUserId)) {
                throw new IllegalArgumentException("You can only publish your own courses");
            }
        }
        course.setIsPublished(publish);
        courseRepository.save(course);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("isPublished", publish);
        result.put("message", publish ? "Course published successfully" : "Course unpublished successfully");
        return result;
    }

    @Override
    public boolean existsById(Long id) {
        return courseRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCourseStatsByCategory() {
        return courseRepository.findAll().stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getCategory().name(),
                        Collectors.counting()
                ));
    }

    private void validateOwnership(Course course, Long requestingUserId, String requestingRole) {
        if (requestingRole != null && requestingRole.equalsIgnoreCase("ADMIN")) {
            return; // Admins can modify any course
        }

        if (course.getInstructorId() == null) {
            return; // Course has no owner, allow modification
        }

        if (requestingUserId == null || !course.getInstructorId().equals(requestingUserId)) {
            throw new IllegalArgumentException(
                "Access denied: You can only modify courses you created"
            );
        }
    }
}
