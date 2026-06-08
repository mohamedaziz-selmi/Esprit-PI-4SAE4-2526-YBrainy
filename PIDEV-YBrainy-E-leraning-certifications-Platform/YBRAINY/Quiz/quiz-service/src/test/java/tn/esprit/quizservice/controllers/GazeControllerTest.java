package tn.esprit.quizservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GazeControllerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GazeController()).build();
    }

    @Test
    @DisplayName("POST /api/quizzes/gaze/start returns sessionId and active=false")
    void startSession_returns200() throws Exception {
        mockMvc.perform(post("/api/quizzes/gaze/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/quizzes/gaze/start with no body returns sessionId")
    void startSession_noBody_returns200() throws Exception {
        mockMvc.perform(post("/api/quizzes/gaze/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    @DisplayName("GET /api/quizzes/gaze/status/{sessionId} returns gaze status")
    void getStatus_returns200() throws Exception {
        mockMvc.perform(get("/api/quizzes/gaze/status/session-abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.looking_at_screen").value(false))
                .andExpect(jsonPath("$.current_focus_score").value(0.0));
    }

    @Test
    @DisplayName("POST /api/quizzes/gaze/stop/{sessionId} returns stopped=true")
    void stopSession_returns200() throws Exception {
        mockMvc.perform(post("/api/quizzes/gaze/stop/session-abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-abc-123"))
                .andExpect(jsonPath("$.stopped").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }
}
