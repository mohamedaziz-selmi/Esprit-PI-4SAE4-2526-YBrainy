package tn.esprit.tpfoyer.Controllers;

import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final EnrollmentClient enrollmentClient;

    @GetMapping("/{certificateId}")
    public ResponseEntity<?> getCertificateById(@PathVariable String certificateId) {
        try {
            Map<String, Object> cert = enrollmentClient.getByCertificate(certificateId);
            return ResponseEntity.ok(cert);
        } catch (Exception e) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Certificate not found: " + certificateId));
        }
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentCertificates(@PathVariable Long studentId) {
        try {
            List<Map<String, Object>> certificates = enrollmentClient.getStudentEnrollments(studentId)
                .stream()
                .filter(e -> e.get("certificateId") != null)
                .map(e -> {
                    Map<String, Object> cert = new HashMap<>();
                    cert.put("courseId", e.get("courseId"));
                    cert.put("studentId", studentId);
                    cert.put("certificateId", e.get("certificateId"));
                    cert.put("completedAt", e.get("completedAt"));
                    cert.put("enrollmentId", e.get("id"));
                    return cert;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(certificates);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
