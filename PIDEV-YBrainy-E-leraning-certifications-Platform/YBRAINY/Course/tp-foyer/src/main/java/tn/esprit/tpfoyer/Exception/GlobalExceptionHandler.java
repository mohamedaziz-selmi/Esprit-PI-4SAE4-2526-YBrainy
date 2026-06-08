package tn.esprit.tpfoyer.Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_KEY = "error";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = ((FieldError) err).getField();
            fieldErrors.put(field, err.getDefaultMessage());
        });
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST);
        body.put("message", "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the maximum allowed limit.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.error("Media type not supported", ex);

        MediaType contentType = ex.getContentType();
        String received = contentType != null ? contentType.toString() : "<null>";
        String supported = ex.getSupportedMediaTypes().toString();

        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type not supported. received=" + received + " supported=" + supported
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String raw = e.getMessage() != null ? e.getMessage() : "Internal server error";
        if (msg.contains("not found") || msg.contains("does not exist")) {
            return ResponseEntity.status(404).body(Map.of(ERROR_KEY, raw));
        }
        if (msg.contains("already exists") || msg.contains("duplicate") || msg.contains("already enrolled")) {
            return ResponseEntity.status(409).body(Map.of(ERROR_KEY, raw));
        }
        return ResponseEntity.status(500).body(Map.of(ERROR_KEY, raw));
    }

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<Map<String, String>> handleFeign(feign.FeignException e) {
        if (e.status() == 404) {
            return ResponseEntity.status(404).body(Map.of(ERROR_KEY, "Resource not found"));
        }
        return ResponseEntity.status(503).body(Map.of(ERROR_KEY, "Upstream service error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        StringBuilder msg = new StringBuilder("An unexpected error occurred: ");
        msg.append(ex.getClass().getSimpleName());
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            msg.append(": ").append(ex.getMessage());
        }
        if (ex.getCause() != null) {
            msg.append(" | cause=").append(ex.getCause().getClass().getSimpleName());
            if (ex.getCause().getMessage() != null && !ex.getCause().getMessage().isBlank()) {
                msg.append(": ").append(ex.getCause().getMessage());
            }
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, msg.toString());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = baseBody(status);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> baseBody(HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put(ERROR_KEY, status.getReasonPhrase());
        return body;
    }
}
