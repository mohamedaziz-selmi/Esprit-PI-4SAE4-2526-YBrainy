package tn.esprit.enrollmentservice.messaging;

import tn.esprit.enrollmentservice.config.RabbitMQConfig;
import tn.esprit.enrollmentservice.entities.Enrollment;
import tn.esprit.enrollmentservice.entities.EnrollmentStatus;
import tn.esprit.enrollmentservice.events.PaymentCompletedEvent;
import tn.esprit.enrollmentservice.repositories.EnrollmentRepository;
import tn.esprit.enrollmentservice.clients.CourseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseClient courseClient;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[RabbitMQ] Received payment.completed: studentId={} courseId={} paymentId={}",
            event.getStudentId(), event.getCourseId(), event.getPaymentIntentId());

        try {
            // Handle learning path (multiple courses)
            if (event.getPathId() != null && !event.getPathId().isBlank()) {
                handlePathEnrollment(event);
                return;
            }

            // Single course enrollment
            if (enrollmentRepository.existsByStudentIdAndCourseId(
                    event.getStudentId(), event.getCourseId())) {
                log.info("[RabbitMQ] Student {} already enrolled in course {}",
                    event.getStudentId(), event.getCourseId());
                return;
            }

            Enrollment enrollment = Enrollment.builder()
                .studentId(event.getStudentId())
                .courseId(event.getCourseId())
                .paymentIntentId(event.getPaymentIntentId())
                .status(EnrollmentStatus.ACTIVE)
                .enrollmentDate(LocalDateTime.now())
                .completionPercentage(0.0)
                .build();

            enrollmentRepository.save(enrollment);
            log.info("[RabbitMQ] Enrollment created: studentId={} courseId={}",
                event.getStudentId(), event.getCourseId());

        } catch (Exception e) {
            log.error("[RabbitMQ] Failed to process payment event: {}", e.getMessage(), e);
        }
    }

    private void handlePathEnrollment(PaymentCompletedEvent event) {
        try {
            Map<String, Object> pathData = courseClient.getCourse(event.getCourseId());
            // pathId contains comma-separated courseIds
            String[] courseIds = event.getPathId().split(",");
            for (String cidStr : courseIds) {
                Long cid = Long.parseLong(cidStr.trim());
                if (!enrollmentRepository.existsByStudentIdAndCourseId(event.getStudentId(), cid)) {
                    Enrollment enrollment = Enrollment.builder()
                        .studentId(event.getStudentId())
                        .courseId(cid)
                        .paymentIntentId(event.getPaymentIntentId())
                        .status(EnrollmentStatus.ACTIVE)
                        .enrollmentDate(LocalDateTime.now())
                        .completionPercentage(0.0)
                        .build();
                    enrollmentRepository.save(enrollment);
                    log.info("[RabbitMQ] Path enrollment created: studentId={} courseId={}",
                        event.getStudentId(), cid);
                }
            }
        } catch (Exception e) {
            log.error("[RabbitMQ] Path enrollment failed: {}", e.getMessage());
        }
    }
}
