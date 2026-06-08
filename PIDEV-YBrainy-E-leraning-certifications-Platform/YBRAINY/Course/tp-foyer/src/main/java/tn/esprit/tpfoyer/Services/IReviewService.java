package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.RatingDistributionDTO;
import tn.esprit.tpfoyer.Dto.ReviewDTO;
import tn.esprit.tpfoyer.Entities.CourseReview;

import java.util.List;

public interface IReviewService {
    List<ReviewDTO> getReviews(Long courseId);
    ReviewDTO addReview(Long courseId, Long studentId, Integer rating, String comment);
    RatingDistributionDTO getRatingStats(Long courseId);
    CourseReview updateReview(Long reviewId, Long studentId, CourseReview updatedReview);
    void deleteReview(Long reviewId, Long studentId);
}
