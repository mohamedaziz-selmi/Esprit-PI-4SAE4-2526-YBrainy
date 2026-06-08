package tn.esprit.quizservice.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quizzes/gaze")
public class GazeController {

    private static final String ACTIVE_KEY = "active";

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSession(
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "sessionId", UUID.randomUUID().toString(),
                ACTIVE_KEY, false
        ));
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String sessionId) {
        return ResponseEntity.ok(Map.of(
                "user_id", "",
                ACTIVE_KEY, false,
                "looking_at_screen", false,
                "current_focus_score", 0.0
        ));
    }

    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<Map<String, Object>> stopSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                ACTIVE_KEY, false,
                "stopped", true
        ));
    }
}
