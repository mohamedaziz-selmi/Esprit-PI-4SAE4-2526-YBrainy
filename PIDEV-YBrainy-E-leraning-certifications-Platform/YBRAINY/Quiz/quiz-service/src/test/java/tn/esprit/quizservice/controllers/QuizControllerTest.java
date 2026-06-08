package tn.esprit.quizservice.controllers;

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
import tn.esprit.quizservice.dto.*;
import tn.esprit.quizservice.services.IQuizService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class QuizControllerTest {

    @Mock IQuizService quizService;

    @InjectMocks QuizController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private QuizDTO sampleQuiz;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        sampleQuiz = QuizDTO.builder()
                .id(1L).courseId(5L).title("Spring Basics Quiz")
                .maxAttempts(3).passingScore(70).build();
    }

    @Test
    @DisplayName("GET /api/quizzes?courseId= returns list of quizzes")
    void getQuizzes_byCourse_returns200() throws Exception {
        when(quizService.getQuizzesByCourse(5L)).thenReturn(List.of(sampleQuiz));

        mockMvc.perform(get("/api/quizzes").param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Spring Basics Quiz"));
    }

    @Test
    @DisplayName("POST /api/quizzes returns 201 with created quiz")
    void createQuiz_returns201() throws Exception {
        when(quizService.createQuiz(eq(5L), any(QuizRequestDTO.class))).thenReturn(sampleQuiz);
        String body = "{\"title\":\"Spring Basics Quiz\",\"maxAttempts\":3,\"passingScore\":70}";

        mockMvc.perform(post("/api/quizzes")
                        .param("courseId", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId} returns 200 with quiz")
    void getQuizById_returns200() throws Exception {
        when(quizService.getQuizById(null, 1L)).thenReturn(sampleQuiz);

        mockMvc.perform(get("/api/quizzes/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId} returns 404 when not found")
    void getQuizById_notFound_returns404() throws Exception {
        when(quizService.getQuizById(null, 99L)).thenThrow(new RuntimeException("Quiz not found"));

        mockMvc.perform(get("/api/quizzes/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/quizzes/{quizId} returns 204")
    void deleteQuiz_returns204() throws Exception {
        doNothing().when(quizService).deleteQuiz(null, 1L);

        mockMvc.perform(delete("/api/quizzes/1"))
                .andExpect(status().isNoContent());

        verify(quizService).deleteQuiz(null, 1L);
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId}/questions returns questions list")
    void getQuestions_returns200() throws Exception {
        QuestionDTO q = QuestionDTO.builder().id(1L).quizId(1L).questionText("What is DI?").build();
        when(quizService.getQuestionsByQuiz(1L)).thenReturn(List.of(q));

        mockMvc.perform(get("/api/quizzes/1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionText").value("What is DI?"));
    }

    @Test
    @DisplayName("POST /api/quizzes/{quizId}/questions returns 201 with new question")
    void addQuestion_returns201() throws Exception {
        QuestionDTO q = QuestionDTO.builder().id(1L).quizId(1L).questionText("What is DI?").build();
        when(quizService.addQuestion(eq(1L), any(QuestionRequestDTO.class))).thenReturn(q);

        mockMvc.perform(post("/api/quizzes/1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"What is DI?\",\"options\":[{\"optionText\":\"Dependency Injection\",\"isCorrect\":true}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/quizzes/{quizId}/questions/{questionId} returns 204")
    void deleteQuestion_returns204() throws Exception {
        doNothing().when(quizService).deleteQuestion(1L, 2L);

        mockMvc.perform(delete("/api/quizzes/1/questions/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId} returns 200 with updated quiz")
    void updateQuiz_returns200() throws Exception {
        QuizDTO updated = QuizDTO.builder().id(1L).title("Updated Quiz").build();
        when(quizService.updateQuiz(eq(1L), any(QuizRequestDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/api/quizzes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Quiz\",\"maxAttempts\":3,\"passingScore\":70}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Quiz"));
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId} returns 404 when quiz not found")
    void updateQuiz_notFound_returns404() throws Exception {
        when(quizService.updateQuiz(eq(99L), any())).thenThrow(new RuntimeException("Quiz not found"));

        mockMvc.perform(put("/api/quizzes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"maxAttempts\":3,\"passingScore\":70}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/quizzes/{quizId}/submit returns 200 with quiz result")
    void submitQuiz_success_returns200() throws Exception {
        QuizResultDTO result = QuizResultDTO.builder()
                .score(85.0).passed(true).build();
        when(quizService.submitQuiz(eq(1L), eq(10L), any(QuizSubmissionDTO.class))).thenReturn(result);

        mockMvc.perform(post("/api/quizzes/1/submit")
                        .param("studentId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85.0))
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    @DisplayName("POST /api/quizzes/{quizId}/submit returns 403 when max attempts reached")
    void submitQuiz_maxAttemptsReached_returns403() throws Exception {
        when(quizService.submitQuiz(eq(1L), eq(10L), any()))
                .thenThrow(new RuntimeException("Max attempts reached"));

        mockMvc.perform(post("/api/quizzes/1/submit")
                        .param("studentId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/quizzes/{quizId}/submit returns 404 when quiz not found")
    void submitQuiz_quizNotFound_returns404() throws Exception {
        when(quizService.submitQuiz(eq(99L), eq(10L), any()))
                .thenThrow(new RuntimeException("Quiz not found"));

        mockMvc.perform(post("/api/quizzes/99/submit")
                        .param("studentId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId}/leaderboard returns leaderboard entries")
    void getLeaderboard_returns200() throws Exception {
        LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                .studentId(10L).studentName("alice").score(95.0).rank(1).build();
        when(quizService.getLeaderboard(1L)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/quizzes/1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].studentName").value("alice"));
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId}/attempts returns attempt info")
    void getAttemptInfo_returns200() throws Exception {
        when(quizService.getQuizById(null, 1L)).thenReturn(sampleQuiz);
        when(quizService.countAttempts(1L, 10L)).thenReturn(1);

        mockMvc.perform(get("/api/quizzes/1/attempts").param("studentId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsUsed").value(1))
                .andExpect(jsonPath("$.attemptsRemaining").value(2))
                .andExpect(jsonPath("$.maxAttempts").value(3));
    }

    @Test
    @DisplayName("GET /api/quizzes/best-score returns best score for student and course")
    void getBestScore_returns200() throws Exception {
        when(quizService.getBestScoreForStudentAndCourse(10L, 5L)).thenReturn(88.5);

        mockMvc.perform(get("/api/quizzes/best-score")
                        .param("studentId", "10")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestScore").value(88.5));
    }

    @Test
    @DisplayName("GET /api/quizzes/student/{studentId}/avg-score returns average score map")
    void getStudentAvgScore_returns200() throws Exception {
        when(quizService.getStudentAvgScore(10L)).thenReturn(Map.of("averageScore", 75.0));

        mockMvc.perform(get("/api/quizzes/student/10/avg-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageScore").value(75.0));
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId}/questions/{questionId} returns 200")
    void updateQuestion_returns200() throws Exception {
        QuestionDTO updated = QuestionDTO.builder().id(2L).quizId(1L).questionText("What is IoC?").build();
        when(quizService.updateQuestion(eq(2L), any(QuestionRequestDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/api/quizzes/1/questions/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"What is IoC?\",\"options\":[{\"optionText\":\"Inversion of Control\",\"isCorrect\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("What is IoC?"));
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId}/questions/{questionId} returns 404 when not found")
    void updateQuestion_notFound_returns404() throws Exception {
        when(quizService.updateQuestion(eq(99L), any())).thenThrow(new RuntimeException("Question not found"));

        mockMvc.perform(put("/api/quizzes/1/questions/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"X\",\"options\":[{\"optionText\":\"Y\",\"isCorrect\":true}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId}/questions/{questionId} returns 500 on generic error")
    void updateQuestion_internalError_returns500() throws Exception {
        when(quizService.updateQuestion(eq(2L), any())).thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(put("/api/quizzes/1/questions/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"X\",\"options\":[{\"optionText\":\"Y\",\"isCorrect\":true}]}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("POST /api/quizzes/{quizId}/submit returns 500 on unexpected error")
    void submitQuiz_internalError_returns500() throws Exception {
        when(quizService.submitQuiz(eq(1L), eq(10L), any()))
                .thenThrow(new RuntimeException("Internal failure"));

        mockMvc.perform(post("/api/quizzes/1/submit")
                        .param("studentId", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("PUT /api/quizzes/{quizId} returns 500 on unexpected error")
    void updateQuiz_internalError_returns500() throws Exception {
        when(quizService.updateQuiz(eq(1L), any())).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(put("/api/quizzes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"maxAttempts\":3,\"passingScore\":70}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/quizzes/{quizId} returns 500 on unexpected error")
    void getQuizById_internalError_returns500() throws Exception {
        when(quizService.getQuizById(null, 1L)).thenThrow(new RuntimeException("DB connection failed"));

        mockMvc.perform(get("/api/quizzes/1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/quizzes/best-score returns 0 when score is null")
    void getBestScore_nullScore_returns0() throws Exception {
        when(quizService.getBestScoreForStudentAndCourse(10L, 5L)).thenReturn(null);

        mockMvc.perform(get("/api/quizzes/best-score")
                        .param("studentId", "10")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestScore").value(0.0));
    }

    @Test
    @DisplayName("GET /api/quizzes/best-score returns 404 body when service throws")
    void getBestScore_exception_returns404Body() throws Exception {
        when(quizService.getBestScoreForStudentAndCourse(10L, 5L))
                .thenThrow(new RuntimeException("No attempts"));

        mockMvc.perform(get("/api/quizzes/best-score")
                        .param("studentId", "10")
                        .param("courseId", "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.bestScore").value(0.0));
    }
}
