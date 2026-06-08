package tn.esprit.tpfoyer.Services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Dto.RatingDistributionDTO;
import tn.esprit.tpfoyer.Dto.ReviewDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.CourseReview;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.CourseReviewRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock CourseReviewRepository reviewRepository;
    @Mock EnrollmentClient enrollmentClient;
    @Mock CourseRepository courseRepository;

    @InjectMocks ReviewServiceImpl reviewService;

    private CourseReview sampleReview(Long id) {
        return CourseReview.builder()
                .id(id).courseId(1L).studentId(10L).rating(4).comment("Good course").build();
    }

    @Test
    @DisplayName("getReviews returns mapped DTOs")
    void getReviews_returnsMappedDTOs() {
        when(reviewRepository.findByCourseIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(sampleReview(1L), sampleReview(2L)));

        List<ReviewDTO> result = reviewService.getReviews(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCourseId()).isEqualTo(1L);
        assertThat(result.get(0).getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("getReviews returns empty list when no reviews")
    void getReviews_empty_returnsEmptyList() {
        when(reviewRepository.findByCourseIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertThat(reviewService.getReviews(1L)).isEmpty();
    }

    @Test
    @DisplayName("addReview throws when student not enrolled")
    void addReview_notEnrolled_throws() {
        when(enrollmentClient.isEnrolled(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.addReview(1L, 10L, 4, "Good"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enrolled");
    }

    @Test
    @DisplayName("addReview throws when student already reviewed")
    void addReview_alreadyReviewed_throws() {
        when(enrollmentClient.isEnrolled(10L, 1L)).thenReturn(true);
        when(reviewRepository.existsByStudentIdAndCourseId(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.addReview(1L, 10L, 5, "Love it"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    @DisplayName("addReview saves review and recalculates rating")
    void addReview_valid_savesAndRecalculatesRating() {
        when(enrollmentClient.isEnrolled(10L, 1L)).thenReturn(true);
        when(reviewRepository.existsByStudentIdAndCourseId(10L, 1L)).thenReturn(false);
        CourseReview saved = sampleReview(5L);
        when(reviewRepository.save(any(CourseReview.class))).thenReturn(saved);
        when(reviewRepository.findAverageRatingByCourseId(1L)).thenReturn(4.0);
        when(reviewRepository.countByCourseId(1L)).thenReturn(1L);
        Course course = Course.builder().id(1L).title("Spring").build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        ReviewDTO result = reviewService.addReview(1L, 10L, 4, "Good course");

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getRating()).isEqualTo(4);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("updateReview throws when review not found")
    void updateReview_notFound_throws() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(99L, 10L, new CourseReview()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Review not found");
    }

    @Test
    @DisplayName("updateReview throws when wrong student attempts update")
    void updateReview_wrongStudent_throws() {
        CourseReview review = sampleReview(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(1L, 99L, new CourseReview()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only edit your own reviews");
    }

    @Test
    @DisplayName("updateReview updates fields and recalculates rating")
    void updateReview_valid_updatesAndRecalculates() {
        CourseReview review = sampleReview(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(CourseReview.class))).thenReturn(review);
        when(reviewRepository.findAverageRatingByCourseId(1L)).thenReturn(4.5);
        when(reviewRepository.countByCourseId(1L)).thenReturn(2L);
        Course course = Course.builder().id(1L).title("Spring").build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        CourseReview updated = CourseReview.builder().rating(5).comment("Even better").build();
        CourseReview result = reviewService.updateReview(1L, 10L, updated);

        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getComment()).isEqualTo("Even better");
    }

    @Test
    @DisplayName("deleteReview throws when review not found")
    void deleteReview_notFound_throws() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(99L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Review not found");
    }

    @Test
    @DisplayName("deleteReview throws when wrong student attempts delete")
    void deleteReview_wrongStudent_throws() {
        CourseReview review = sampleReview(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only delete your own reviews");
    }

    @Test
    @DisplayName("deleteReview deletes and recalculates rating")
    void deleteReview_valid_deletesAndRecalculates() {
        CourseReview review = sampleReview(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        doNothing().when(reviewRepository).delete(review);
        when(reviewRepository.findAverageRatingByCourseId(1L)).thenReturn(3.5);
        when(reviewRepository.countByCourseId(1L)).thenReturn(3L);
        Course course = Course.builder().id(1L).title("Spring").build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        reviewService.deleteReview(1L, 10L);

        verify(reviewRepository).delete(review);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("getRatingStats returns distribution with averages")
    void getRatingStats_withReviews_returnsDistribution() {
        when(reviewRepository.findAverageRatingByCourseId(1L)).thenReturn(4.15);
        when(reviewRepository.countByCourseId(1L)).thenReturn(10L);
        when(reviewRepository.countByRatingAndCourseId(1L, 5)).thenReturn(5L);
        when(reviewRepository.countByRatingAndCourseId(1L, 4)).thenReturn(3L);
        when(reviewRepository.countByRatingAndCourseId(1L, 3)).thenReturn(2L);
        when(reviewRepository.countByRatingAndCourseId(1L, 2)).thenReturn(0L);
        when(reviewRepository.countByRatingAndCourseId(1L, 1)).thenReturn(0L);

        RatingDistributionDTO result = reviewService.getRatingStats(1L);

        assertThat(result.getAverageRating()).isEqualTo(4.2);
        assertThat(result.getTotalReviews()).isEqualTo(10L);
        assertThat(result.getCount5()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getRatingStats with no reviews returns 0.0 average")
    void getRatingStats_noReviews_returnsZeroAverage() {
        when(reviewRepository.findAverageRatingByCourseId(1L)).thenReturn(null);
        when(reviewRepository.countByCourseId(1L)).thenReturn(0L);
        when(reviewRepository.countByRatingAndCourseId(anyLong(), anyInt())).thenReturn(0L);

        RatingDistributionDTO result = reviewService.getRatingStats(1L);

        assertThat(result.getAverageRating()).isEqualTo(0.0);
        assertThat(result.getTotalReviews()).isEqualTo(0L);
    }
}
