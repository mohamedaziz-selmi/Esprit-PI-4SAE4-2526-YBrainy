package tn.esprit.tpfoyer.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Dto.MlConversionDTO;
import tn.esprit.tpfoyer.Dto.MlQualityDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Services.MLServiceClient;
import tn.esprit.tpfoyer.Clients.QuizClient;
import tn.esprit.tpfoyer.Clients.LessonClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MLController {

    private final MLServiceClient mlClient;
    private final QuizClient quizClient;
    private final LessonClient lessonClient;
    private final EnrollmentClient enrollmentClient;
    private final CourseRepository courseRepository;

    // GET /api/ml/recommendations?category=PROGRAMMING&level=BEGINNER&topN=5
    // OR  /api/ml/recommendations?studentId=1&topN=5  (derives category/level from enrollments)
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "BEGINNER") String level,
            @RequestParam(defaultValue = "5") int topN,
            @RequestParam(required = false) Long studentId) {

        List<Long> enrolledCourseIds = new ArrayList<>();

        if (studentId != null) {
            List<Map<String, Object>> enrollments = Collections.emptyList();
            try { enrollments = enrollmentClient.getStudentEnrollments(studentId); } catch (Exception ignored) {}

            enrolledCourseIds = enrollments.stream()
                .filter(e -> e.get("courseId") instanceof Number)
                .map(e -> ((Number) e.get("courseId")).longValue())
                .collect(Collectors.toList());

            List<Course> courses = courseRepository.findAllById(enrolledCourseIds);

            Map<String, Long> categoryCounts = courses.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(c -> c.getCategory().name(), Collectors.counting()));

            String dominantCategory = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("PROGRAMMING");

            Map<String, Long> levelCounts = courses.stream()
                .filter(c -> c.getLevel() != null)
                .collect(Collectors.groupingBy(c -> c.getLevel().name(), Collectors.counting()));

            String dominantLevel = levelCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("BEGINNER");

            category = dominantCategory;
            level = dominantLevel;
        }

        if (category == null || category.isBlank()) {
            category = "PROGRAMMING";
        }

        return ResponseEntity.ok(mlClient.getRecommendations(category, level, topN, enrolledCourseIds));
    }

    // GET /api/ml/student/{studentId}/conversion
    @GetMapping("/student/{studentId}/conversion")
    public ResponseEntity<?> getConversionPrediction(@PathVariable Long studentId) {
        try {
            List<Map<String, Object>> enrollments = Collections.emptyList();
            try { enrollments = enrollmentClient.getStudentEnrollments(studentId); } catch (Exception ignored) {}

            double completionRate = enrollments.stream()
                    .mapToDouble(e -> e.get("completionPercentage") instanceof Number n ? n.doubleValue() : 0.0)
                    .average().orElse(0.0) / 100.0;

            List<Map<String, Object>> allProgress = new ArrayList<>();
            for (Map<String, Object> e : enrollments) {
                Long eid = e.get("id") instanceof Number n ? n.longValue() : null;
                if (eid != null) {
                    try { allProgress.addAll(lessonClient.getProgressByEnrollment(eid)); } catch (Exception ignored) {}
                }
            }

            double timeSpent = allProgress.stream()
                    .mapToDouble(lp -> lp.get("timeSpentSeconds") instanceof Number n ? n.doubleValue() / 60.0 : 0.0)
                    .sum();

            long videosWatched = allProgress.stream()
                    .filter(lp -> "COMPLETED".equals(lp.get("status")))
                    .count();

            long paidEnrollments = enrollments.stream()
                    .filter(e -> e.get("paymentIntentId") != null)
                    .count();

            double quizScores = 50.0;
            try {
                Map<String, Object> quizData = quizClient.getAvgScore(studentId);
                if (quizData != null && quizData.get("avgScore") != null) {
                    quizScores = ((Number) quizData.get("avgScore")).doubleValue();
                }
            } catch (Exception ignored) {}

            int lessonInteractions = allProgress.size();

            MlConversionDTO result = mlClient.predictConversion(
                    timeSpent, completionRate, quizScores,
                    (double) videosWatched, (double) lessonInteractions, 0.0);

            Map<String, Object> response = new HashMap<>();
            response.put("conversionProbability", result.getConversionProbability());
            response.put("conversionLabel", result.getConversionLabel());
            response.put("percentage", result.getPercentage());
            response.put("totalEnrollments", enrollments.size());
            response.put("paidEnrollments", paidEnrollments);
            response.put("avgCompletionRate", Math.round(completionRate * 100));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "conversionProbability", 0.5,
                    "conversionLabel", "MEDIUM",
                    "percentage", 50.0));
        }
    }

    // GET /api/ml/course/{courseId}/quality
    @GetMapping("/course/{courseId}/quality")
    public ResponseEntity<?> getCourseQuality(@PathVariable Long courseId) {
        try {
            System.out.println("[ML Quality] courseId=" + courseId);
            Course course = courseRepository.findById(courseId).orElseThrow();
            List<Map<String, Object>> lessons = lessonClient.getLessonsByCourse(courseId);
            System.out.println("[ML Quality] lessons count=" + lessons.size());

            int numLessons = lessons.size();

            double contentDuration = lessons.stream()
                    .mapToDouble(l -> l.get("durationMinutes") instanceof Number n ? n.doubleValue() : 0)
                    .sum() / 60.0;

            long distinctTypes = lessons.stream()
                    .filter(l -> l.get("type") != null)
                    .map(l -> (String) l.get("type"))
                    .distinct().count();

            long videoLessons = lessons.stream()
                    .filter(l -> "VIDEO_UPLOAD".equals(l.get("type")) || "YOUTUBE_EMBED".equals(l.get("type")))
                    .count();

            double pctVideo = numLessons > 0 ? (double) videoLessons / numLessons : 0.0;
            int certEncoded = Boolean.TRUE.equals(course.getOffersCertificate()) ? 1 : 0;

            int levelEncoded = 0;
            if (course.getLevel() != null) {
                switch (course.getLevel()) {
                    case INTERMEDIATE -> levelEncoded = 1;
                    case ADVANCED -> levelEncoded = 2;
                    default -> levelEncoded = 0;
                }
            }

            double rating = course.getRating() != null ? course.getRating() : 0.0;
            int ratingCount = course.getRatingCount() != null ? course.getRatingCount() : 0;

            MlQualityDTO quality = mlClient.predictQuality(
                    numLessons, contentDuration,
                    (int) distinctTypes, pctVideo,
                    numLessons, certEncoded, levelEncoded,
                    rating, ratingCount);

            System.out.println("[ML Quality] Flask response: " + quality);
            return ResponseEntity.ok(quality);
        } catch (Exception e) {
            System.out.println("[ML Quality] Exception for course " + courseId + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new MlQualityDTO("UNKNOWN", 0.0, 0.0, false, null, null, null));
        }
    }

    // POST /api/ml/courses/quality-batch
    @PostMapping("/courses/quality-batch")
    public ResponseEntity<?> getCourseQualityBatch(@RequestBody List<Long> courseIds) {
        Map<Long, MlQualityDTO> results = new HashMap<>();
        for (Long courseId : courseIds) {
            try {
                Course course = courseRepository.findById(courseId).orElse(null);
                if (course == null) continue;

                List<Map<String, Object>> lessons = lessonClient.getLessonsByCourse(courseId);
                int numLessons = lessons.size();

                double contentDuration = lessons.stream()
                        .mapToDouble(l -> l.get("durationMinutes") instanceof Number n ? n.doubleValue() : 0)
                        .sum() / 60.0;

                long distinctTypes = lessons.stream()
                        .filter(l -> l.get("type") != null)
                        .map(l -> (String) l.get("type"))
                        .distinct().count();

                long videoLessons = lessons.stream()
                        .filter(l -> "VIDEO_UPLOAD".equals(l.get("type")) || "YOUTUBE_EMBED".equals(l.get("type")))
                        .count();

                double pctVideo = numLessons > 0 ? (double) videoLessons / numLessons : 0.0;
                int certEncoded = Boolean.TRUE.equals(course.getOffersCertificate()) ? 1 : 0;

                int levelEncoded = 0;
                if (course.getLevel() != null) {
                    switch (course.getLevel()) {
                        case INTERMEDIATE -> levelEncoded = 1;
                        case ADVANCED -> levelEncoded = 2;
                        default -> levelEncoded = 0;
                    }
                }

                double rating = course.getRating() != null ? course.getRating() : 0.0;
                int ratingCount = course.getRatingCount() != null ? course.getRatingCount() : 0;

                results.put(courseId, mlClient.predictQuality(
                        numLessons, contentDuration,
                        (int) distinctTypes, pctVideo,
                        numLessons, certEncoded, levelEncoded,
                        rating, ratingCount));
            } catch (Exception e) {
                System.out.println("[ML Quality Batch] Skipping course " + courseId + ": " + e.getMessage());
            }
        }
        return ResponseEntity.ok(results);
    }

    // GET /api/ml/forecast?steps=6
    @GetMapping("/forecast")
    public ResponseEntity<?> getForecast(@RequestParam(defaultValue = "6") int steps) {
        return ResponseEntity.ok(mlClient.getForecast(steps));
    }

    // GET /api/ml/admin/conversion-stats — DSO1 aggregation across all students
    @GetMapping("/admin/conversion-stats")
    public ResponseEntity<?> getAdminConversionStats() {
        try {
            List<Map<String, Object>> allEnrollments = Collections.emptyList();
            try { allEnrollments = enrollmentClient.getAllEnrollments(); } catch (Exception ignored) {}

            Map<Long, List<Map<String, Object>>> byStudent = allEnrollments.stream()
                .filter(e -> e.get("studentId") instanceof Number)
                .collect(Collectors.groupingBy(e -> ((Number) e.get("studentId")).longValue()));

            int totalStudents = byStudent.size();

            Set<Long> convertedStudentIds = byStudent.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                    .anyMatch(e -> e.get("paymentIntentId") != null))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

            int convertedStudents = convertedStudentIds.size();

            Set<Long> freeOnlyStudentIds = byStudent.keySet().stream()
                .filter(sid -> !convertedStudentIds.contains(sid))
                .collect(Collectors.toSet());

            int freeOnlyStudents = freeOnlyStudentIds.size();

            double totalProbability = 0.0;
            int highPotentialCount = 0;
            int processed = 0;

            for (Long studentId : freeOnlyStudentIds) {
                try {
                    List<Map<String, Object>> studentEnrollments = byStudent.get(studentId);

                    List<Long> enrollmentIds = studentEnrollments.stream()
                        .filter(e -> e.get("id") instanceof Number)
                        .map(e -> ((Number) e.get("id")).longValue())
                        .collect(Collectors.toList());

                    List<Map<String, Object>> allProgress = new ArrayList<>();
                    if (!enrollmentIds.isEmpty()) {
                        try { allProgress = lessonClient.getProgressByEnrollmentIds(enrollmentIds); } catch (Exception ignored) {}
                    }

                    double timeSpent = allProgress.stream()
                        .mapToDouble(lp -> lp.get("timeSpentSeconds") instanceof Number n ? n.doubleValue() / 60.0 : 0)
                        .sum();

                    double completionRate = studentEnrollments.stream()
                        .mapToDouble(e -> e.get("completionPercentage") instanceof Number n ? n.doubleValue() / 100.0 : 0)
                        .average().orElse(0.0);

                    double quizScores = 50.0;
                    try {
                        Map<String, Object> quizData = quizClient.getAvgScore(studentId);
                        if (quizData != null && quizData.get("avgScore") != null) {
                            quizScores = ((Number) quizData.get("avgScore")).doubleValue();
                        }
                    } catch (Exception ignored) {}

                    long videosWatched = allProgress.stream()
                        .filter(lp -> "COMPLETED".equals(lp.get("status"))).count();
                    long lessonInteractions = allProgress.size();

                    MlConversionDTO result = mlClient.predictConversion(
                        timeSpent, completionRate, quizScores,
                        (double) videosWatched, (double) lessonInteractions, 0.0);

                    if (result != null && result.getPercentage() != null) {
                        double prob = result.getPercentage();
                        totalProbability += prob;
                        if (prob >= 70.0) highPotentialCount++;
                        processed++;
                    }
                } catch (Exception e) {
                    System.out.println("[ConversionStats] Failed for student " + studentId + ": " + e.getMessage());
                }
            }

            double avgConversionProbability = processed > 0 ? totalProbability / processed : 0.0;
            double conversionRate = totalStudents > 0 ? (convertedStudents * 100.0) / totalStudents : 0.0;

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalStudents", totalStudents);
            stats.put("convertedStudents", convertedStudents);
            stats.put("freeOnlyStudents", freeOnlyStudents);
            stats.put("conversionRate", Math.round(conversionRate * 10.0) / 10.0);
            stats.put("avgConversionProbability", Math.round(avgConversionProbability * 10.0) / 10.0);
            stats.put("highPotentialCount", highPotentialCount);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            System.out.println("[ConversionStats] Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
