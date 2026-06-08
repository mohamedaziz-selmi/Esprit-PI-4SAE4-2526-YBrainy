package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.MlPredictResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class MlPredictService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ml.predict.url:http://localhost:5001/predict}")
    private String predictUrl;

    public MlPredictService() {
        this.restClient = RestClient.create();
    }

    public MlPredictResponse predict(String title, String body, String tags) {
        try {
            Map<String, String> payload = Map.of(
                    "title", title != null ? title : "",
                    "body",  body  != null ? body.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim() : "",
                    "tags",  tags  != null ? tags : ""
            );

            String json = restClient.post()
                    .uri(predictUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            MlPredictResponse resp = objectMapper.readValue(json, MlPredictResponse.class);
            resp.setAvailable(true);
            return resp;
        } catch (Exception e) {
            log.warn("ML predict service unavailable: {}", e.getMessage());
            return MlPredictResponse.builder()
                    .available(false)
                    .label("UNKNOWN")
                    .confidence(0.0)
                    .color("#6b7280")
                    .message("Prediction service unavailable")
                    .build();
        }
    }
}
