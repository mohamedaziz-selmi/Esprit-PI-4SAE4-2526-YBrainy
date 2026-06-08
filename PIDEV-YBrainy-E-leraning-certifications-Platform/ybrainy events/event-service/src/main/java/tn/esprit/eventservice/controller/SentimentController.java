package tn.esprit.eventservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionRequestDto;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionResponseDto;
import tn.esprit.eventservice.service.PythonSentimentService;

@RestController
@RequestMapping("/api/sentiment")
@CrossOrigin(
        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true"
)
@AllArgsConstructor
public class SentimentController {

    private final PythonSentimentService pythonSentimentService;

    @PostMapping("/python/predict")
    public MlSentimentPredictionResponseDto predictPythonSentiment(
            @RequestBody MlSentimentPredictionRequestDto request
    ) {
        return pythonSentimentService.predictSentiment(request.getComment());
    }
}
