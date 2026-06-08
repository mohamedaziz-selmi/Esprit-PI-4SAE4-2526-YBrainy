package tn.esprit.tpfoyer.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.tpfoyer.Dto.RatingDistributionDTO;
import tn.esprit.tpfoyer.Dto.ReviewDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.CourseReview;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.CourseReviewRepository;

import java.util.List;
import java.util.stream.Collectors;



@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements IReviewService {

    private final CourseReviewRepository reviewRepository;
    private final EnrollmentClient enrollmentClient;
    private final CourseRepository courseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReviewDTO> getReviews(Long courseId) {
        return reviewRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ReviewDTO addReview(Long courseId, Long studentId, Integer rating, String comment) {
        if (!Boolean.TRUE.equals(enrollmentClient.isEnrolled(studentId, courseId))) {
            throw new IllegalStateException("Student is not enrolled in this course");
        }
        if (reviewRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new IllegalStateException("You have already reviewed this course.");
        }
        CourseReview review = CourseReview.builder()
                .courseId(courseId)
                .studentId(studentId)
                .rating(rating)
                .comment(comment)
                .build();
        ReviewDTO saved = toDTO(reviewRepository.save(review));
        recalculateCourseRating(courseId);
        return saved;
    }

    @Override
    public CourseReview updateReview(Long reviewId, Long studentId, CourseReview updatedReview) {
        CourseReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Verify ownership
        if (!review.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("You can only edit your own reviews");
        }

        // Update fields
        review.setRating(updatedReview.getRating());
        review.setComment(updatedReview.getComment());

        CourseReview saved = reviewRepository.save(review);
        recalculateCourseRating(review.getCourseId());
        return saved;
    }

    @Override
    public void deleteReview(Long reviewId, Long studentId) {
        CourseReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Verify ownership
        if (!review.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("You can only delete your own reviews");
        }

        Long courseId = review.getCourseId();
        reviewRepository.delete(review);
        recalculateCourseRating(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingDistributionDTO getRatingStats(Long courseId) {
        Double avg = reviewRepository.findAverageRatingByCourseId(courseId);
        long total = reviewRepository.countByCourseId(courseId);
        return RatingDistributionDTO.builder()
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0)
                .totalReviews(total)
                .count5(reviewRepository.countByRatingAndCourseId(courseId, 5))
                .count4(reviewRepository.countByRatingAndCourseId(courseId, 4))
                .count3(reviewRepository.countByRatingAndCourseId(courseId, 3))
                .count2(reviewRepository.countByRatingAndCourseId(courseId, 2))
                .count1(reviewRepository.countByRatingAndCourseId(courseId, 1))
                .build();
    }

    private void recalculateCourseRating(Long courseId) {
        Double avgRating = reviewRepository.findAverageRatingByCourseId(courseId);
        long reviewCount = reviewRepository.countByCourseId(courseId);
        Course course = courseRepository.findById(courseId).orElseThrow();
        course.setRating(avgRating != null ? avgRating : 0.0);
        course.setRatingCount((int) reviewCount);
        courseRepository.save(course);
    }

    private ReviewDTO toDTO(CourseReview r) {
        return ReviewDTO.builder()
                .id(r.getId())
                .courseId(r.getCourseId())
                .studentId(r.getStudentId())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                .build();
    }
}
