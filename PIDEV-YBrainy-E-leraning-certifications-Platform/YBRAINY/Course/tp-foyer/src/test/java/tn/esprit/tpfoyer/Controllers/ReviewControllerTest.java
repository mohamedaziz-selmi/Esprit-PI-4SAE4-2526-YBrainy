package tn.esprit.tpfoyer.Controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.tpfoyer.Dto.RatingDistributionDTO;
import tn.esprit.tpfoyer.Dto.ReviewDTO;
import tn.esprit.tpfoyer.Entities.CourseReview;
import tn.esprit.tpfoyer.Services.IReviewService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock IReviewService reviewService;

    @InjectMocks ReviewController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/reviews/stats returns rating distribution")
    void getRatingStats_returns200() throws Exception {
        RatingDistributionDTO stats = RatingDistributionDTO.builder()
                .averageRating(4.2).totalReviews(10L).count5(5L).count4(3L).count3(2L).build();
        when(reviewService.getRatingStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/courses/1/reviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.2))
                .andExpect(jsonPath("$.totalReviews").value(10));
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/reviews returns paginated reviews")
    void getReviews_returns200() throws Exception {
        ReviewDTO review = ReviewDTO.builder().id(1L).courseId(1L).studentId(10L).rating(5)
                .comment("Great course!").build();
        when(reviewService.getReviews(1L)).thenReturn(List.of(review));

        mockMvc.perform(get("/api/courses/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("Great course!"));
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/reviews returns 201 with new review")
    void addReview_returns201() throws Exception {
        ReviewDTO review = ReviewDTO.builder().id(1L).courseId(1L).studentId(10L).rating(4)
                .comment("Good content").build();
        when(reviewService.addReview(eq(1L), eq(10L), eq(4), eq("Good content"))).thenReturn(review);

        mockMvc.perform(post("/api/courses/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"rating\":4,\"comment\":\"Good content\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/reviews returns 409 when already reviewed")
    void addReview_alreadyReviewed_returns409() throws Exception {
        when(reviewService.addReview(eq(1L), eq(10L), anyInt(), any()))
                .thenThrow(new IllegalStateException("You have already reviewed this course."));

        mockMvc.perform(post("/api/courses/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"rating\":3,\"comment\":\"Again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/reviews returns 403 when not enrolled")
    void addReview_notEnrolled_returns403() throws Exception {
        when(reviewService.addReview(eq(1L), eq(10L), anyInt(), any()))
                .thenThrow(new IllegalStateException("Student is not enrolled in this course"));

        mockMvc.perform(post("/api/courses/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"rating\":3,\"comment\":\"Test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/courses/{courseId}/reviews/{reviewId} returns updated review")
    void updateReview_returns200() throws Exception {
        CourseReview updated = CourseReview.builder().id(1L).courseId(1L).studentId(10L)
                .rating(5).comment("Updated comment").build();
        when(reviewService.updateReview(eq(1L), eq(10L), any(CourseReview.class))).thenReturn(updated);

        mockMvc.perform(put("/api/courses/1/reviews/1")
                        .param("studentId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Updated comment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Updated comment"));
    }

    @Test
    @DisplayName("DELETE /api/courses/{courseId}/reviews/{reviewId} returns 204")
    void deleteReview_returns204() throws Exception {
        doNothing().when(reviewService).deleteReview(1L, 10L);

        mockMvc.perform(delete("/api/courses/1/reviews/1").param("studentId", "10"))
                .andExpect(status().isNoContent());

        verify(reviewService).deleteReview(1L, 10L);
    }
}
