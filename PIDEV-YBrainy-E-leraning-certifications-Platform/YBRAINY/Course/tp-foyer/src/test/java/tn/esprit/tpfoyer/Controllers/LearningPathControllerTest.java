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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Dto.LearningPathDTO;
import tn.esprit.tpfoyer.Dto.SaveLearningPathRequestDTO;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.LearningPathRepository;
import tn.esprit.tpfoyer.Services.ILearningPathService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LearningPathControllerTest {

    @Mock ILearningPathService learningPathService;
    @Mock LearningPathRepository learningPathRepository;
    @Mock CourseRepository courseRepository;
    @Mock EnrollmentClient enrollmentClient;

    @InjectMocks LearningPathController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "stripeSecretKey", "sk_test_fake");
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:4200");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/learning-paths/generate with null goal returns 400")
    void generate_nullGoal_returns400() throws Exception {
        mockMvc.perform(post("/api/learning-paths/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Learning path goal is required"));

        verifyNoInteractions(learningPathService);
    }

    @Test
    @DisplayName("POST /api/learning-paths/generate with short goal returns 400")
    void generate_shortGoal_returns400() throws Exception {
        mockMvc.perform(post("/api/learning-paths/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"goal\":\"AI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Goal must be at least 5 characters"));

        verifyNoInteractions(learningPathService);
    }

    @Test
    @DisplayName("POST /api/learning-paths/generate with valid goal returns 200")
    void generate_validGoal_returns200() throws Exception {
        LearningPathDTO path = LearningPathDTO.builder().id(1L).goal("Learn Spring Boot").build();
        when(learningPathService.generate(10L, "Learn Spring Boot")).thenReturn(path);

        mockMvc.perform(post("/api/learning-paths/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"goal\":\"Learn Spring Boot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.goal").value("Learn Spring Boot"));
    }

    @Test
    @DisplayName("POST /api/learning-paths/generate returns 500 when service throws")
    void generate_serviceError_returns500() throws Exception {
        when(learningPathService.generate(any(), anyString()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        mockMvc.perform(post("/api/learning-paths/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"goal\":\"Learn something useful\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("AI service unavailable"));
    }

    @Test
    @DisplayName("GET /api/learning-paths/student/{studentId} returns saved paths")
    void getSavedPaths_returns200() throws Exception {
        LearningPathDTO path = LearningPathDTO.builder().id(1L).studentId(10L).goal("Learn Kotlin").build();
        when(learningPathService.getSavedPaths(10L)).thenReturn(List.of(path));

        mockMvc.perform(get("/api/learning-paths/student/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].goal").value("Learn Kotlin"));
    }

    @Test
    @DisplayName("DELETE /api/learning-paths/{pathId} returns 204")
    void deletePath_returns204() throws Exception {
        doNothing().when(learningPathService).deletePath(1L);

        mockMvc.perform(delete("/api/learning-paths/1"))
                .andExpect(status().isNoContent());

        verify(learningPathService).deletePath(1L);
    }

    @Test
    @DisplayName("POST /api/learning-paths/save returns saved learning path")
    void save_returns200() throws Exception {
        LearningPathDTO saved = LearningPathDTO.builder().id(5L).studentId(10L).goal("Deep Learning").build();
        when(learningPathService.save(any(SaveLearningPathRequestDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/learning-paths/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":10,\"goal\":\"Deep Learning\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }
}
