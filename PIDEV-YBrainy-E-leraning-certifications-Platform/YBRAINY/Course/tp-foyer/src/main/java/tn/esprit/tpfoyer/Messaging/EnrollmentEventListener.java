package tn.esprit.tpfoyer.Messaging;

import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Events.EnrollmentCompletedEvent;
import tn.esprit.tpfoyer.Services.ICertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnrollmentEventListener {

    private final ICertificateService certificateService;
    private final EnrollmentClient enrollmentClient;

    @RabbitListener(queues = "enrollment.completed.queue")
    public void handleEnrollmentCompleted(EnrollmentCompletedEvent event) {
        log.info("[RabbitMQ] Received enrollment.completed: studentId={} courseId={}",
            event.getStudentId(), event.getCourseId());
        try {
            String certId = certificateService.generateCertificate(
                event.getCourseId(), event.getStudentId());
            log.info("[RabbitMQ] Certificate issued for studentId={} courseId={} certId={}",
                event.getStudentId(), event.getCourseId(), certId);
            try {
                enrollmentClient.updateCertificate(event.getEnrollmentId(), certId);
                log.info("[RabbitMQ] Certificate ID {} written back to enrollment {}",
                    certId, event.getEnrollmentId());
            } catch (Exception ex) {
                log.warn("[RabbitMQ] Could not write certificate ID back to enrollment: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("[RabbitMQ] Certificate generation failed: {}", e.getMessage());
        }
    }
}
