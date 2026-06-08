package tn.esprit.lessonservice.controllers;

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
import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonProgress;
import tn.esprit.lessonservice.entities.ProgressStatus;
import tn.esprit.lessonservice.services.ILessonService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LessonControllerTest {

    @Mock ILessonService lessonService;

    @InjectMocks LessonController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Lesson sampleLesson;
    private LessonProgress sampleProgress;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        sampleLesson = new Lesson();
        sampleLesson.setId(1L);
        sampleLesson.setCourseId(5L);
        sampleLesson.setTitle("Introduction");
        sampleLesson.setOrderIndex(1);

        sampleProgress = new LessonProgress();
        sampleProgress.setId(1L);
        sampleProgress.setEnrollmentId(10L);
        sampleProgress.setLessonId(1L);
        sampleProgress.setStatus(ProgressStatus.COMPLETED);
    }

    @Test
    @DisplayName("GET /api/lessons/course/{courseId} returns lessons list")
    void getLessonsByCourse_returns200() throws Exception {
        when(lessonService.getLessonsByCourse(5L)).thenReturn(List.of(sampleLesson));

        mockMvc.perform(get("/api/lessons/course/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Introduction"));
    }

    @Test
    @DisplayName("GET /api/lessons/{id} returns single lesson")
    void getLesson_returns200() throws Exception {
        when(lessonService.getLessonById(1L)).thenReturn(sampleLesson);

        mockMvc.perform(get("/api/lessons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseId").value(5));
    }

    @Test
    @DisplayName("GET /api/lessons/course/{courseId}/lesson/{lessonId} returns lesson by course")
    void getLessonByCourse_returns200() throws Exception {
        when(lessonService.getLessonByIdAndCourse(1L, 5L)).thenReturn(sampleLesson);

        mockMvc.perform(get("/api/lessons/course/5/lesson/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Introduction"));
    }

    @Test
    @DisplayName("GET /api/lessons/course/{courseId}/count returns lesson count")
    void getLessonCount_returns200() throws Exception {
        when(lessonService.countLessonsByCourse(5L)).thenReturn(7L);

        mockMvc.perform(get("/api/lessons/course/5/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    @DisplayName("POST /api/lessons/course/{courseId} creates lesson and returns 200")
    void createLesson_returns200() throws Exception {
        when(lessonService.createLesson(eq(5L), any(Lesson.class))).thenReturn(sampleLesson);

        mockMvc.perform(post("/api/lessons/course/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Introduction\",\"orderIndex\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/lessons/course/{courseId}/lesson/{lessonId} updates and returns 200")
    void updateLesson_returns200() throws Exception {
        Lesson updated = new Lesson();
        updated.setId(1L);
        updated.setCourseId(5L);
        updated.setTitle("Updated Title");
        when(lessonService.updateLesson(eq(5L), eq(1L), any(Lesson.class))).thenReturn(updated);

        mockMvc.perform(put("/api/lessons/course/5/lesson/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @DisplayName("DELETE /api/lessons/course/{courseId}/lesson/{lessonId} returns 204")
    void deleteLesson_returns204() throws Exception {
        doNothing().when(lessonService).deleteLesson(5L, 1L);

        mockMvc.perform(delete("/api/lessons/course/5/lesson/1"))
                .andExpect(status().isNoContent());

        verify(lessonService).deleteLesson(5L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/lessons/course/{courseId}/all returns 204")
    void deleteAllLessonsByCourse_returns204() throws Exception {
        doNothing().when(lessonService).deleteAllLessonsByCourse(5L);

        mockMvc.perform(delete("/api/lessons/course/5/all"))
                .andExpect(status().isNoContent());

        verify(lessonService).deleteAllLessonsByCourse(5L);
    }

    @Test
    @DisplayName("GET /api/lessons/search returns matching lessons")
    void searchByTitle_returns200() throws Exception {
        when(lessonService.searchLessonsByTitle("intro")).thenReturn(List.of(sampleLesson));

        mockMvc.perform(get("/api/lessons/search").param("title", "intro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Introduction"));
    }

    @Test
    @DisplayName("POST /api/lessons/progress tracks progress and returns 200")
    void trackProgress_returns200() throws Exception {
        when(lessonService.trackProgress(10L, 1L, "COMPLETED", null))
                .thenReturn(sampleProgress);

        mockMvc.perform(post("/api/lessons/progress")
                        .param("enrollmentId", "10")
                        .param("lessonId", "1")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/lessons/progress/enrollment/{enrollmentId} returns progress list")
    void getProgress_returns200() throws Exception {
        when(lessonService.getProgressByEnrollment(10L)).thenReturn(List.of(sampleProgress));

        mockMvc.perform(get("/api/lessons/progress/enrollment/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrollmentId").value(10));
    }

    @Test
    @DisplayName("GET /api/lessons/progress/enrollment/{enrollmentId}/completed-count returns count")
    void getCompletedCount_returns200() throws Exception {
        when(lessonService.countCompletedLessons(10L)).thenReturn(4L);

        mockMvc.perform(get("/api/lessons/progress/enrollment/10/completed-count"))
                .andExpect(status().isOk())
                .andExpect(content().string("4"));
    }

    @Test
    @DisplayName("POST /api/lessons/progress/time tracks time and returns 200")
    void trackTimeOnly_returns200() throws Exception {
        when(lessonService.trackTimeOnly(10L, 1L, 120L)).thenReturn(sampleProgress);

        mockMvc.perform(post("/api/lessons/progress/time")
                        .param("enrollmentId", "10")
                        .param("lessonId", "1")
                        .param("timeSpent", "120"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/lessons/progress/by-enrollments returns progress for multiple enrollments")
    void getProgressByEnrollments_returns200() throws Exception {
        when(lessonService.getProgressByEnrollmentIds(anyList()))
                .thenReturn(List.of(sampleProgress));

        mockMvc.perform(post("/api/lessons/progress/by-enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[10, 11, 12]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonId").value(1));
    }

    @Test
    @DisplayName("GET /api/lessons/progress/active-since returns active enrollment IDs")
    void getActiveEnrollmentIdsSince_returns200() throws Exception {
        when(lessonService.getActiveEnrollmentIdsSince(any(LocalDateTime.class)))
                .thenReturn(List.of(10L, 11L));

        mockMvc.perform(get("/api/lessons/progress/active-since")
                        .param("since", "2025-01-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(10));
    }

    @Test
    @DisplayName("DELETE /api/lessons/progress/enrollments returns 204")
    void deleteProgressByEnrollments_returns204() throws Exception {
        doNothing().when(lessonService).deleteProgressByEnrollmentIds(anyList());

        mockMvc.perform(delete("/api/lessons/progress/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[10, 11]"))
                .andExpect(status().isNoContent());
    }
}
