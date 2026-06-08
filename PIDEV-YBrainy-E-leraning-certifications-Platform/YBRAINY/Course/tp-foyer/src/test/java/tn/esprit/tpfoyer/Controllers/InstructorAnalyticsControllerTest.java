package tn.esprit.tpfoyer.Controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.tpfoyer.Dto.CourseAnalyticsDTO;
import tn.esprit.tpfoyer.Services.ICourseService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InstructorAnalyticsControllerTest {

    @Mock ICourseService courseService;

    @InjectMocks InstructorAnalyticsController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/instructor/courses/{courseId}/analytics returns analytics DTO")
    void getCourseAnalytics_returns200() throws Exception {
        CourseAnalyticsDTO analytics = CourseAnalyticsDTO.builder()
                .courseId(1L).courseTitle("Spring Boot")
                .totalEnrollments(50).completedStudents(20)
                .averageRating(4.5).totalReviews(30)
                .build();
        when(courseService.getCourseAnalytics(1L)).thenReturn(analytics);

        mockMvc.perform(get("/api/instructor/courses/1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(1))
                .andExpect(jsonPath("$.totalEnrollments").value(50))
                .andExpect(jsonPath("$.averageRating").value(4.5));
    }

    @Test
    @DisplayName("GET /api/instructor/courses/{courseId}/analytics returns empty DTO when no data")
    void getCourseAnalytics_emptyData_returns200() throws Exception {
        CourseAnalyticsDTO empty = CourseAnalyticsDTO.builder().courseId(2L).totalEnrollments(0).build();
        when(courseService.getCourseAnalytics(2L)).thenReturn(empty);

        mockMvc.perform(get("/api/instructor/courses/2/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEnrollments").value(0));
    }
}
