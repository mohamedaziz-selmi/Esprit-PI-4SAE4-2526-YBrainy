package tn.esprit.tpfoyer.Controllers;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.Dto.GenerateLearningPathRequestDTO;
import tn.esprit.tpfoyer.Dto.LearningPathDTO;
import tn.esprit.tpfoyer.Dto.SaveLearningPathRequestDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.LearningPath;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.LearningPathRepository;
import tn.esprit.tpfoyer.Services.ILearningPathService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning-paths")
@RequiredArgsConstructor
public class LearningPathController {

    private final ILearningPathService learningPathService;
    private final LearningPathRepository learningPathRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentClient enrollmentClient;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateLearningPathRequestDTO request) {
        if (request.getGoal() == null || request.getGoal().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Learning path goal is required"));
        }
        if (request.getGoal().trim().length() < 5) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Goal must be at least 5 characters"));
        }
        try {
            LearningPathDTO result = learningPathService.generate(
                request.getStudentId(), request.getGoal());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<LearningPathDTO> save(@RequestBody SaveLearningPathRequestDTO request) {
        return ResponseEntity.ok(learningPathService.save(request));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<LearningPathDTO>> getSavedPaths(@PathVariable Long studentId) {
        return ResponseEntity.ok(learningPathService.getSavedPaths(studentId));
    }

    @DeleteMapping("/{pathId}")
    public ResponseEntity<Void> deletePath(@PathVariable Long pathId) {
        learningPathService.deletePath(pathId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{pathId}/enroll")
    public ResponseEntity<?> enrollInPath(
            @PathVariable Long pathId,
            @RequestParam Long studentId) {
        try {
            LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new RuntimeException("Learning path not found"));

            if (path.getCourseIds() == null || path.getCourseIds().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No courses in path"));
            }

            List<Long> courseIds = Arrays.stream(path.getCourseIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

            List<Course> courses = courseRepository.findAllById(courseIds);

            List<Course> paidCourses = new ArrayList<>();
            for (Course course : courses) {
                if (Boolean.TRUE.equals(enrollmentClient.isEnrolled(studentId, course.getId()))) {
                    continue;
                }
                boolean isFree = course.getPrice() == null
                    || course.getPrice().compareTo(BigDecimal.ZERO) == 0;
                if (isFree) {
                    enrollmentClient.enroll(studentId, course.getId());
                } else {
                    paidCourses.add(course);
                }
            }

            if (paidCourses.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "hasPaidCourses", false,
                    "enrolled", true,
                    "checkoutUrl", ""));
            }

            // Create Stripe checkout for paid courses
            Stripe.apiKey = stripeSecretKey;
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/learning-paths?enrolled=true&pathId=" + pathId)
                .setCancelUrl(frontendUrl + "/courses")
                .putMetadata("pathId", pathId.toString())
                .putMetadata("studentId", studentId.toString());

            for (Course paid : paidCourses) {
                long unitAmount = paid.getPrice().multiply(BigDecimal.valueOf(100)).longValue();
                paramsBuilder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(unitAmount)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(paid.getTitle())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );
            }

            Session session = Session.create(paramsBuilder.build());
            return ResponseEntity.ok(Map.of(
                "hasPaidCourses", true,
                "checkoutUrl", session.getUrl()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
