package esprit.tn.breadandbutteruser.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Object> handleMissingRequestPart(MissingServletRequestPartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "The required upload part '" + ex.getRequestPartName() + "' is missing.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuth(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid or missing token");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccess(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden: You don't have permission");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return new ResponseEntity<>(body, status);
    }
}
