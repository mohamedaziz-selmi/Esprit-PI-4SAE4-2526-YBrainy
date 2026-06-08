package tn.esprit.tpfoyer.Controllers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import tn.esprit.tpfoyer.Config.RabbitMQConfig;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Dto.CheckoutConfirmRequest;
import tn.esprit.tpfoyer.Dto.CheckoutConfirmResponse;
import tn.esprit.tpfoyer.Dto.CheckoutSessionRequest;
import tn.esprit.tpfoyer.Dto.CheckoutSessionResponse;
import tn.esprit.tpfoyer.Dto.PaymentCompletedEvent;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.LearningPath;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.LearningPathRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RabbitTemplate rabbitTemplate;
    private final LearningPathRepository learningPathRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentClient enrollmentClient;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.publishable.key}")
    private String stripePublishableKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestBody CheckoutSessionRequest request) throws Exception {

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(Math.round(request.getPrice() * 100))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(request.getCourseTitle())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .setSuccessUrl(frontendUrl + "/courses/" + request.getCourseId()
                        + "?payment=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/courses/" + request.getCourseId())
                .putMetadata("courseId", request.getCourseId().toString())
                .putMetadata("studentId", request.getStudentId().toString())
                .build();

        Session session = Session.create(params);

        return ResponseEntity.ok(new CheckoutSessionResponse(session.getId(), session.getUrl()));
    }

    @PostMapping("/confirm-checkout-session")
    public ResponseEntity<CheckoutConfirmResponse> confirmCheckoutSession(
            @RequestBody CheckoutConfirmRequest request) throws Exception {

        Session session = Session.retrieve(
                request.getSessionId(),
                SessionRetrieveParams.builder().addExpand("line_items").build(),
                null);

        if (!"paid".equals(session.getPaymentStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(null);
        }

        String paymentIntentId = session.getPaymentIntent() != null ? session.getPaymentIntent() : "unknown";
        Map<String, String> metadata = session.getMetadata();
        Long studentId = metadata.containsKey("studentId") ? Long.parseLong(metadata.get("studentId")) : null;

        if (studentId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        if (metadata.containsKey("pathId")) {
            Long pathId = Long.parseLong(metadata.get("pathId"));
            LearningPath path = learningPathRepository.findById(pathId).orElse(null);
            if (path != null && path.getCourseIds() != null) {
                for (String cidStr : path.getCourseIds().split(",")) {
                    try {
                        Long courseId = Long.parseLong(cidStr.trim());
                        enrollmentClient.enrollWithPayment(studentId, courseId, paymentIntentId);
                    } catch (Exception e) {
                        log.warn("[Confirm] Failed to enroll for course {}: {}", cidStr, e.getMessage());
                    }
                }
            }
        } else if (metadata.containsKey("courseId")) {
            Long courseId = Long.parseLong(metadata.get("courseId"));
            try {
                enrollmentClient.enrollWithPayment(studentId, courseId, paymentIntentId);
            } catch (feign.FeignException.Conflict ignored) {
                // already enrolled — fine
            }
        }

        var enrollments = enrollmentClient.getStudentEnrollments(studentId);
        return ResponseEntity.ok(new CheckoutConfirmResponse(request.getSessionId(), paymentIntentId, enrollments));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Webhook error: " + e.getMessage());
        }

        if ("checkout.session.completed".equals(event.getType())) {
            String rawJson = event.toJson();
            JsonObject eventJson = JsonParser.parseString(rawJson).getAsJsonObject();
            JsonObject sessionObj = eventJson
                .getAsJsonObject("data")
                .getAsJsonObject("object");

            Map<String, String> metadata = new HashMap<>();
            if (sessionObj.has("metadata") && !sessionObj.get("metadata").isJsonNull()) {
                sessionObj.getAsJsonObject("metadata").entrySet()
                    .forEach(e -> metadata.put(e.getKey(), e.getValue().getAsString()));
            }

            String paymentIntentId = sessionObj.has("payment_intent")
                && !sessionObj.get("payment_intent").isJsonNull()
                ? sessionObj.get("payment_intent").getAsString()
                : "unknown";

            Long studentId = metadata.containsKey("studentId")
                ? Long.parseLong(metadata.get("studentId"))
                : null;

            if (studentId == null) {
                log.warn("[Webhook] No studentId in metadata, skipping");
                return ResponseEntity.ok("skipped");
            }

            if (metadata.containsKey("pathId")) {
                // Learning path payment — publish an event per paid course
                Long pathId = Long.parseLong(metadata.get("pathId"));
                log.info("[Webhook] Path payment: pathId={} studentId={}", pathId, studentId);

                LearningPath path = learningPathRepository.findById(pathId).orElse(null);
                if (path != null && path.getCourseIds() != null) {
                    for (String cidStr : path.getCourseIds().split(",")) {
                        try {
                            Long courseId = Long.parseLong(cidStr.trim());
                            Course course = courseRepository.findById(courseId).orElse(null);
                            if (course != null && course.getPrice() != null
                                    && course.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                                publishPaymentEvent(studentId, courseId, paymentIntentId, metadata.get("pathId"));
                                log.info("[Webhook] Published payment event: studentId={} courseId={}",
                                    studentId, courseId);
                            }
                        } catch (Exception e) {
                            log.warn("[Webhook] Failed to publish event for course {}: {}",
                                cidStr, e.getMessage());
                        }
                    }
                }

            } else if (metadata.containsKey("courseId")) {
                // Single course payment
                Long courseId = Long.parseLong(metadata.get("courseId"));
                log.info("[Webhook] Single course payment: courseId={} studentId={}", courseId, studentId);
                publishPaymentEvent(studentId, courseId, paymentIntentId, null);

            } else {
                log.warn("[Webhook] checkout.session.completed has no courseId or pathId in metadata");
            }
        }

        return ResponseEntity.ok("Received");
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of("publishableKey", stripePublishableKey));
    }

    private void publishPaymentEvent(Long studentId, Long courseId, String paymentIntentId, String pathId) {
        PaymentCompletedEvent evt = PaymentCompletedEvent.builder()
            .studentId(studentId)
            .courseId(courseId)
            .paymentIntentId(paymentIntentId)
            .pathId(pathId)
            .build();
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.PAYMENT_EXCHANGE,
            RabbitMQConfig.PAYMENT_ROUTING_KEY,
            evt);
        log.info("[Payment] Published payment.completed event for studentId={} courseId={} pathId={}",
            studentId, courseId, pathId);
    }
}
