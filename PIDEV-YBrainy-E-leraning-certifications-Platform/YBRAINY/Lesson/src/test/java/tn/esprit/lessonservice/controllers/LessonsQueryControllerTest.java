package tn.esprit.lessonservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonType;
import tn.esprit.lessonservice.services.ILessonService;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LessonsQueryControllerTest {

    @Mock ILessonService lessonService;
    @InjectMocks LessonsQueryController controller;

    MockMvc mockMvc;

    private Lesson sampleLesson() {
        Lesson l = new Lesson();
        l.setId(1L);
        l.setCourseId(5L);
        l.setTitle("Intro to Spring");
        l.setType(LessonType.VIDEO_UPLOAD);
        l.setOrderIndex(0);
        l.setDurationMinutes(45);
        l.setContents(Collections.emptyList());
        return l;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/courses/lessons?courseId=5 returns paginated lessons")
    void getAllLessons_withCourseId_returnsPaginatedList() throws Exception {
        when(lessonService.getLessonsByCourse(5L)).thenReturn(List.of(sampleLesson()));

        mockMvc.perform(get("/api/courses/lessons").param("courseId", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Intro to Spring"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/courses/lessons without courseId returns empty page")
    void getAllLessons_noCourseId_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/courses/lessons"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));

        verifyNoInteractions(lessonService);
    }

    @Test
    @DisplayName("GET /api/courses/lessons?courseId=5 with empty course returns empty page")
    void getAllLessons_emptyCourse_returnsEmptyPage() throws Exception {
        when(lessonService.getLessonsByCourse(5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/courses/lessons").param("courseId", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/courses/lessons?courseId=5&page=0&size=1 respects pagination")
    void getAllLessons_withPagination_respectsPageSize() throws Exception {
        Lesson l2 = sampleLesson();
        l2.setId(2L);
        l2.setTitle("Second Lesson");
        when(lessonService.getLessonsByCourse(5L)).thenReturn(List.of(sampleLesson(), l2));

        mockMvc.perform(get("/api/courses/lessons")
            .param("courseId", "5")
            .param("page", "0")
            .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1));
    }
}
