package tn.esprit.enrollmentservice.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("not found") || msg.contains("does not exist")) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
        if (msg.contains("already exists") || msg.contains("duplicate") || msg.contains("already enrolled")) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<Map<String, String>> handleFeign(feign.FeignException e) {
        if (e.status() == 404) {
            return ResponseEntity.status(404).body(Map.of("error", "Resource not found"));
        }
        return ResponseEntity.status(503).body(Map.of("error", "Upstream service error"));
    }
}
