package tn.esprit.eventservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionResponseDto;
import tn.esprit.eventservice.service.PythonSentimentService;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SentimentController.class)
class SentimentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PythonSentimentService pythonSentimentService;

    @Test
    @DisplayName("POST /api/sentiment/python/predict returns sentiment response")
    void predictPythonSentiment_returnsResponse() throws Exception {
        when(pythonSentimentService.predictSentiment("This event was excellent and very useful."))
                .thenReturn(new MlSentimentPredictionResponseDto(
                        "This event was excellent and very useful.",
                        "positive",
                        Map.of("positive", 0.258402, "neutral", -0.115479, "negative", -1.242053)
                ));

        mockMvc.perform(post("/api/sentiment/python/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "This event was excellent and very useful."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("positive"))
                .andExpect(jsonPath("$.comment").value("This event was excellent and very useful."))
                .andExpect(jsonPath("$.scores.positive").value(0.258402));

        verify(pythonSentimentService).predictSentiment("This event was excellent and very useful.");
    }
}
