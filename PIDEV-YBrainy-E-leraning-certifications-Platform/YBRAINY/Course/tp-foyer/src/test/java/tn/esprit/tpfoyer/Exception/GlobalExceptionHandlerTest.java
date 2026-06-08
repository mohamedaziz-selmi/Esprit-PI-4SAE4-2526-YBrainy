package tn.esprit.tpfoyer.Exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleNotFound returns 404")
    void handleNotFound_returns404() {
        ResponseEntity<Map<String, Object>> r = handler.handleNotFound(
                new ResourceNotFoundException("Course not found"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsKey("message");
    }

    @Test
    @DisplayName("handleBadRequest returns 400")
    void handleBadRequest_returns400() {
        ResponseEntity<Map<String, Object>> r = handler.handleBadRequest(
                new IllegalArgumentException("invalid input"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("message", "invalid input");
    }

    @Test
    @DisplayName("handleRuntime routes 'not found' message to 404")
    void handleRuntime_notFound_returns404() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntime(
                new RuntimeException("resource not found"));
        assertThat(r.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("handleRuntime routes 'already exists' message to 409")
    void handleRuntime_alreadyExists_returns409() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntime(
                new RuntimeException("already exists in system"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("handleRuntime routes 'already enrolled' message to 409")
    void handleRuntime_alreadyEnrolled_returns409() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntime(
                new RuntimeException("already enrolled"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("handleRuntime routes generic message to 500")
    void handleRuntime_generic_returns500() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntime(
                new RuntimeException("something went wrong"));
        assertThat(r.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("handleRuntime with null message returns 500")
    void handleRuntime_nullMessage_returns500() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntime(
                new RuntimeException((String) null));
        assertThat(r.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("handleMediaTypeNotSupported returns 415 with content type info")
    void handleMediaTypeNotSupported_returns415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map<String, Object>> r = handler.handleMediaTypeNotSupported(ex);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(r.getBody()).containsKey("message");
    }

    @Test
    @DisplayName("handleMediaTypeNotSupported with null content type does not throw NPE")
    void handleMediaTypeNotSupported_nullContentType_noNPE() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("type not supported");
        ResponseEntity<Map<String, Object>> r = handler.handleMediaTypeNotSupported(ex);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(r.getBody().get("message").toString()).contains("<null>");
    }

    @Test
    @DisplayName("handleGeneric returns 500 with class name in message")
    void handleGeneric_returns500() {
        ResponseEntity<Map<String, Object>> r = handler.handleGeneric(
                new Exception("unexpected failure"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().get("message").toString()).contains("Exception");
    }

    @Test
    @DisplayName("handleFileTooLarge returns 413")
    void handleFileTooLarge_returns413() {
        org.springframework.web.multipart.MaxUploadSizeExceededException ex =
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1024);
        ResponseEntity<Map<String, Object>> r = handler.handleFileTooLarge(ex);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
