package tn.esprit.tpfoyer.Controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.Dto.RatingDistributionDTO;
import tn.esprit.tpfoyer.Dto.ReviewCreateDTO;
import tn.esprit.tpfoyer.Dto.ReviewDTO;
import tn.esprit.tpfoyer.Entities.CourseReview;
import tn.esprit.tpfoyer.Services.IReviewService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final IReviewService reviewService;

    // GET /api/courses/{courseId}/reviews/stats
    @GetMapping("/api/courses/{courseId}/reviews/stats")
    public ResponseEntity<RatingDistributionDTO> getRatingStats(@PathVariable Long courseId) {
        return ResponseEntity.ok(reviewService.getRatingStats(courseId));
    }

    // GET /api/courses/{courseId}/reviews
    // Returns { content: [...], totalElements: N } matching Angular's SpringPage<ApiReview> shape
    @GetMapping("/api/courses/{courseId}/reviews")
    public ResponseEntity<Map<String, Object>> getReviews(@PathVariable Long courseId) {
        List<ReviewDTO> reviews = reviewService.getReviews(courseId);
        return ResponseEntity.ok(Map.of("content", reviews, "totalElements", (long) reviews.size()));
    }

    // POST /api/courses/{courseId}/reviews
    // Body: { studentId: number, rating: number, comment: string }
    @PostMapping("/api/courses/{courseId}/reviews")
    public ResponseEntity<?> addReview(
            @PathVariable Long courseId,
            @RequestBody @Valid ReviewCreateDTO dto) {
        try {
            ReviewDTO review = reviewService.addReview(courseId, dto.getStudentId(), dto.getRating(), dto.getComment());
            return ResponseEntity.status(HttpStatus.CREATED).body(review);
        } catch (IllegalStateException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already reviewed")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.FORBIDDEN;
            return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
        }
    }

    // PUT /api/courses/{courseId}/reviews/{reviewId}
    @PutMapping("/api/courses/{courseId}/reviews/{reviewId}")
    public ResponseEntity<CourseReview> updateReview(
            @PathVariable Long courseId,
            @PathVariable Long reviewId,
            @RequestParam Long studentId,
            @RequestBody CourseReview updatedReview) {
        return ResponseEntity.ok(reviewService.updateReview(reviewId, studentId, updatedReview));
    }

    // DELETE /api/courses/{courseId}/reviews/{reviewId}
    @DeleteMapping("/api/courses/{courseId}/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long courseId,
            @PathVariable Long reviewId,
            @RequestParam Long studentId) {
        reviewService.deleteReview(reviewId, studentId);
        return ResponseEntity.noContent().build();
    }
}
