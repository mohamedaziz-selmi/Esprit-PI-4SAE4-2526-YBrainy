package tn.esprit.enrollmentservice.controllers;

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
import tn.esprit.enrollmentservice.entities.Enrollment;
import tn.esprit.enrollmentservice.entities.EnrollmentStatus;
import tn.esprit.enrollmentservice.services.IEnrollmentService;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

    @Mock IEnrollmentService enrollmentService;

    @InjectMocks EnrollmentController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Enrollment sampleEnrollment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        sampleEnrollment = Enrollment.builder()
                .id(1L).studentId(10L).courseId(5L)
                .status(EnrollmentStatus.ACTIVE)
                .completionPercentage(0.0).build();
    }

    @Test
    @DisplayName("POST /api/enrollments returns 200 with enrollment")
    void enroll_returns200() throws Exception {
        when(enrollmentService.enroll(10L, 5L)).thenReturn(sampleEnrollment);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"courseId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(10))
                .andExpect(jsonPath("$.courseId").value(5));
    }

    @Test
    @DisplayName("GET /api/enrollments returns all enrollments")
    void getAllEnrollments_returns200() throws Exception {
        when(enrollmentService.getAllEnrollments()).thenReturn(List.of(sampleEnrollment));

        mockMvc.perform(get("/api/enrollments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(10));
    }

    @Test
    @DisplayName("GET /api/enrollments/student/{studentId} returns student's enrollments")
    void getStudentEnrollments_returns200() throws Exception {
        when(enrollmentService.getStudentEnrollments(10L)).thenReturn(List.of(sampleEnrollment));

        mockMvc.perform(get("/api/enrollments/student/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(5));
    }

    @Test
    @DisplayName("GET /api/enrollments/exists returns true when enrolled")
    void isEnrolled_returnsTrue() throws Exception {
        when(enrollmentService.isEnrolled(10L, 5L)).thenReturn(true);

        mockMvc.perform(get("/api/enrollments/exists")
                        .param("studentId", "10")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /api/enrollments/exists returns false when not enrolled")
    void isEnrolled_returnsFalse() throws Exception {
        when(enrollmentService.isEnrolled(10L, 99L)).thenReturn(false);

        mockMvc.perform(get("/api/enrollments/exists")
                        .param("studentId", "10")
                        .param("courseId", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("GET /api/enrollments/monthly-counts returns list")
    void getMonthlyCounts_returns200() throws Exception {
        when(enrollmentService.getMonthlyEnrollmentCounts())
                .thenReturn(List.of(Map.of("month", "2025-01", "count", 12)));

        mockMvc.perform(get("/api/enrollments/monthly-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(12));
    }

    @Test
    @DisplayName("DELETE /api/enrollments/course/{courseId} returns 204")
    void deleteEnrollmentsByCourse_returns204() throws Exception {
        doNothing().when(enrollmentService).deleteEnrollmentsByCourse(5L);

        mockMvc.perform(delete("/api/enrollments/course/5"))
                .andExpect(status().isNoContent());

        verify(enrollmentService).deleteEnrollmentsByCourse(5L);
    }

    @Test
    @DisplayName("GET /api/enrollments/course/{courseId}/progress returns progress map")
    void getCourseProgress_returns200() throws Exception {
        when(enrollmentService.getCourseProgress(5L, 10L))
                .thenReturn(Map.of("completionPercentage", 50.0, "completedLessons", 3));

        mockMvc.perform(get("/api/enrollments/course/5/progress")
                        .param("studentId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(50.0));
    }

    @Test
    @DisplayName("POST /api/enrollments/course/{courseId}/lessons/{lessonId}/complete returns 200")
    void markComplete_returns200() throws Exception {
        when(enrollmentService.markLessonComplete(5L, 3L, 10L)).thenReturn(sampleEnrollment);
        when(enrollmentService.getCourseProgress(5L, 10L))
                .thenReturn(Map.of("completionPercentage", 33.3));

        mockMvc.perform(post("/api/enrollments/course/5/lessons/3/complete")
                        .param("studentId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(33.3));
    }

    @Test
    @DisplayName("POST /api/enrollments/with-payment returns 200")
    void enrollWithPayment_returns200() throws Exception {
        when(enrollmentService.enrollWithPayment(10L, 5L, "pi_test123"))
                .thenReturn(sampleEnrollment);

        mockMvc.perform(post("/api/enrollments/with-payment")
                        .param("studentId", "10")
                        .param("courseId", "5")
                        .param("paymentIntentId", "pi_test123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/enrollments/students/{studentId}/dashboard returns dashboard data")
    void getStudentDashboard_returns200() throws Exception {
        when(enrollmentService.getStudentDashboard(10L))
                .thenReturn(Map.of("totalCourses", 3, "completedCourses", 1));

        mockMvc.perform(get("/api/enrollments/students/10/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(3));
    }

    @Test
    @DisplayName("GET /api/enrollments/course/{courseId}/list returns enrollments for course")
    void getEnrollmentsByCourse_returns200() throws Exception {
        when(enrollmentService.getEnrollmentsByCourse(5L)).thenReturn(List.of(sampleEnrollment));

        mockMvc.perform(get("/api/enrollments/course/5/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(10));
    }

    @Test
    @DisplayName("PATCH /api/enrollments/{id}/certificate returns 200")
    void updateCertificateId_returns200() throws Exception {
        doNothing().when(enrollmentService).updateCertificateId(1L, "cert-abc-123");

        mockMvc.perform(patch("/api/enrollments/1/certificate")
                        .param("certificateId", "cert-abc-123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/enrollments/course/{courseId}/lessons/{lessonId}/time returns 200")
    void trackTime_returns200() throws Exception {
        doNothing().when(enrollmentService).trackTimeSpent(5L, 3L, 10L, 120);

        mockMvc.perform(post("/api/enrollments/course/5/lessons/3/time")
                        .param("studentId", "10")
                        .param("seconds", "120"))
                .andExpect(status().isOk());

        verify(enrollmentService).trackTimeSpent(5L, 3L, 10L, 120);
    }

    @Test
    @DisplayName("GET /api/enrollments/student/{studentId}/course/{courseId} returns enrollment")
    void getEnrollment_returns200() throws Exception {
        when(enrollmentService.getEnrollment(10L, 5L)).thenReturn(sampleEnrollment);

        mockMvc.perform(get("/api/enrollments/student/10/course/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(10))
                .andExpect(jsonPath("$.courseId").value(5));
    }

    @Test
    @DisplayName("GET /api/enrollments/by-certificate/{certificateId} returns enrollment")
    void getByCertificate_returns200() throws Exception {
        when(enrollmentService.getByCertificateId("cert-xyz-999")).thenReturn(sampleEnrollment);

        mockMvc.perform(get("/api/enrollments/by-certificate/cert-xyz-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/enrollments/{enrollmentId}/certificate returns 200")
    void updateCertificate_returns200() throws Exception {
        doNothing().when(enrollmentService).updateCertificate(1L, "cert-new-456");

        mockMvc.perform(put("/api/enrollments/1/certificate")
                        .param("certificateId", "cert-new-456"))
                .andExpect(status().isOk());

        verify(enrollmentService).updateCertificate(1L, "cert-new-456");
    }
}
