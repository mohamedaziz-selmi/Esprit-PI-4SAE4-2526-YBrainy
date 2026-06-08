package tn.esprit.lessonservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonContent;
import tn.esprit.lessonservice.entities.LessonType;
import tn.esprit.lessonservice.services.IFileStorageService;
import tn.esprit.lessonservice.services.ILessonService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LessonFileControllerTest {

    @Mock ILessonService lessonService;
    @Mock IFileStorageService fileStorageService;

    LessonFileController controller;
    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        controller = new LessonFileController(lessonService, fileStorageService, objectMapper, validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private Lesson sampleLesson() {
        Lesson l = new Lesson();
        l.setId(1L);
        l.setCourseId(5L);
        l.setTitle("Intro to Spring");
        l.setType(LessonType.YOUTUBE_EMBED);
        l.setContentUrl("https://youtube.com/watch?v=abc");
        l.setOrderIndex(0);
        l.setDurationMinutes(30);
        l.setContents(new ArrayList<>());
        return l;
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/lessons returns list of lessons")
    void getLessons_returns200() throws Exception {
        when(lessonService.getLessonsByCourse(5L)).thenReturn(List.of(sampleLesson()));

        mockMvc.perform(get("/api/courses/5/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Intro to Spring"));
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/lessons/{lessonId} returns a single lesson")
    void getLesson_returns200() throws Exception {
        when(lessonService.getLessonByIdAndCourse(1L, 5L)).thenReturn(sampleLesson());

        mockMvc.perform(get("/api/courses/5/lessons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseId").value(5));
    }

    @Test
    @DisplayName("DELETE /api/courses/{courseId}/lessons/{lessonId} with no contents returns 204")
    void deleteLesson_noContents_returns204() throws Exception {
        Lesson lesson = sampleLesson();
        lesson.setContents(Collections.emptyList());
        when(lessonService.getLessonByIdAndCourse(1L, 5L)).thenReturn(lesson);
        doNothing().when(lessonService).deleteLesson(5L, 1L);

        mockMvc.perform(delete("/api/courses/5/lessons/1"))
                .andExpect(status().isNoContent());

        verify(lessonService).deleteLesson(5L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/courses/{courseId}/lessons/{lessonId} with file content deletes file")
    void deleteLesson_withFileContent_deletesFile() throws Exception {
        LessonContent content = new LessonContent();
        content.setId(10L);
        content.setType(LessonType.VIDEO_UPLOAD);
        content.setContentUrl("/uploads/video.mp4");
        content.setOrderIndex(0);

        Lesson lesson = sampleLesson();
        lesson.setContents(List.of(content));
        when(lessonService.getLessonByIdAndCourse(1L, 5L)).thenReturn(lesson);
        doNothing().when(fileStorageService).deleteFile("/uploads/video.mp4");
        doNothing().when(lessonService).deleteLesson(5L, 1L);

        mockMvc.perform(delete("/api/courses/5/lessons/1"))
                .andExpect(status().isNoContent());

        verify(fileStorageService).deleteFile("/uploads/video.mp4");
        verify(lessonService).deleteLesson(5L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/courses/{courseId}/lessons/{lessonId} when lesson not found still deletes")
    void deleteLesson_lessonNotFound_stillCallsDelete() throws Exception {
        when(lessonService.getLessonByIdAndCourse(99L, 5L))
                .thenThrow(new RuntimeException("Lesson not found"));
        doNothing().when(lessonService).deleteLesson(5L, 99L);

        mockMvc.perform(delete("/api/courses/5/lessons/99"))
                .andExpect(status().isNoContent());

        verify(lessonService).deleteLesson(5L, 99L);
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/lessons with YouTube URL creates lesson")
    void createLesson_withYoutubeUrl_returns201() throws Exception {
        String metaJson = "{\"title\":\"Intro Lesson\",\"durationMinutes\":30}";
        MockMultipartFile lessonPart = new MockMultipartFile(
                "lesson", "lesson.json", MediaType.APPLICATION_JSON_VALUE, metaJson.getBytes());

        Lesson created = sampleLesson();
        when(lessonService.createLesson(eq(5L), any(Lesson.class))).thenReturn(created);

        mockMvc.perform(multipart("/api/courses/5/lessons")
                        .file(lessonPart)
                        .param("youtubeUrls", "https://youtube.com/watch?v=abc"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Intro to Spring"));
    }

    @Test
    @DisplayName("GET /api/courses/{courseId}/lessons returns empty list when no lessons")
    void getLessons_empty_returns200() throws Exception {
        when(lessonService.getLessonsByCourse(5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/courses/5/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/lessons with video file creates lesson")
    void createLesson_withVideoFile_returns201() throws Exception {
        String metaJson = "{\"title\":\"Video Lesson\",\"durationMinutes\":45}";
        MockMultipartFile lessonPart = new MockMultipartFile(
                "lesson", "lesson.json", MediaType.APPLICATION_JSON_VALUE, metaJson.getBytes());
        MockMultipartFile videoPart = new MockMultipartFile(
                "videos", "lecture.mp4", "video/mp4", "fake-video".getBytes());

        when(fileStorageService.storeLessonContent(any(), eq(LessonType.VIDEO_UPLOAD)))
                .thenReturn("/uploads/lecture.mp4");
        Lesson created = sampleLesson();
        created.setType(LessonType.VIDEO_UPLOAD);
        created.setContentUrl("/uploads/lecture.mp4");
        when(lessonService.createLesson(eq(5L), any(Lesson.class))).thenReturn(created);

        mockMvc.perform(multipart("/api/courses/5/lessons")
                        .file(lessonPart)
                        .file(videoPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(fileStorageService).storeLessonContent(any(), eq(LessonType.VIDEO_UPLOAD));
    }

    @Test
    @DisplayName("PUT /api/courses/{courseId}/lessons/{lessonId} with YouTube URL updates lesson")
    void updateLesson_withYoutubeUrl_returns200() throws Exception {
        String metaJson = "{\"title\":\"Updated Lesson\",\"durationMinutes\":20}";
        MockMultipartFile lessonPart = new MockMultipartFile(
                "lesson", "lesson.json", MediaType.APPLICATION_JSON_VALUE, metaJson.getBytes());

        Lesson existing = sampleLesson();
        Lesson updated = sampleLesson();
        updated.setTitle("Updated Lesson");

        when(lessonService.getLessonByIdAndCourse(1L, 5L)).thenReturn(existing);
        when(lessonService.updateLesson(eq(5L), eq(1L), any(Lesson.class))).thenReturn(updated);

        mockMvc.perform(multipart("/api/courses/5/lessons/1")
                        .file(lessonPart)
                        .param("youtubeUrls", "https://youtube.com/watch?v=new")
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/lessons with missing lesson part returns 400")
    void createLesson_missingLessonPart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/courses/5/lessons"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/courses/{courseId}/lessons with sequence creates lesson via sequence path")
    void createLesson_withSequence_returns201() throws Exception {
        String metaJson = "{\"title\":\"Seq Lesson\",\"durationMinutes\":20}";
        MockMultipartFile lessonPart = new MockMultipartFile(
                "lesson", "lesson.json", MediaType.APPLICATION_JSON_VALUE, metaJson.getBytes());
        String sequenceJson = "[{\"type\":\"YOUTUBE_EMBED\",\"youtubeIndex\":0}]";
        MockMultipartFile sequencePart = new MockMultipartFile(
                "sequence", "sequence.json", MediaType.APPLICATION_JSON_VALUE, sequenceJson.getBytes());

        Lesson created = sampleLesson();
        when(lessonService.createLesson(eq(5L), any(Lesson.class))).thenReturn(created);

        mockMvc.perform(multipart("/api/courses/5/lessons")
                        .file(lessonPart)
                        .file(sequencePart)
                        .param("youtubeUrls", "https://youtube.com/watch?v=seq"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }
}
