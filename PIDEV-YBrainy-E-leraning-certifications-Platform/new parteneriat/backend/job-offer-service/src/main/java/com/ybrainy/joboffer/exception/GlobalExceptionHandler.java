package com.ybrainy.joboffer.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), req.getRequestURI());
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of(), req.getRequestURI());
  }

  @ExceptionHandler(ExternalServiceException.class)
  public ResponseEntity<ApiError> handleExternalService(ExternalServiceException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), List.of(), req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<String> details =
        ex.getBindingResult().getAllErrors().stream()
            .map(err -> err instanceof FieldError fe ? fe.getField() + ": " + fe.getDefaultMessage() : err.getDefaultMessage())
            .toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", details, req.getRequestURI());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
    List<String> details = ex.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).toList();
    return build(HttpStatus.BAD_REQUEST, "Constraint violation", details, req.getRequestURI());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "Endpoint not found", List.of(ex.getMessage()), req.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", List.of(ex.getMessage()), req.getRequestURI());
  }

  private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details, String path) {
    ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, details, path);
    return ResponseEntity.status(status).body(body);
  }
}
