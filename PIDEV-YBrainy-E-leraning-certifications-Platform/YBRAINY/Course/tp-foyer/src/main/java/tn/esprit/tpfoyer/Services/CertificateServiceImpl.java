package tn.esprit.tpfoyer.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.tpfoyer.Clients.EnrollmentClient;
import tn.esprit.tpfoyer.Clients.LessonClient;
import tn.esprit.tpfoyer.Clients.QuizClient;
import tn.esprit.tpfoyer.Clients.UserClient;
import tn.esprit.tpfoyer.Dto.VerificationResponseDTO;
import tn.esprit.tpfoyer.Entities.*;
import tn.esprit.tpfoyer.Repositories.*;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateServiceImpl implements ICertificateService {

    private final CourseRepository courseRepository;
    private final EnrollmentClient enrollmentClient;
    private final LessonClient lessonClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final QuizClient quizClient;
    private final UserClient userClient;

    @Value("${openrouter.api.key}")
    private String openRouterKey;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Colors ────────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY_DARK_BLUE = new DeviceRgb(26, 54, 93);
    private static final DeviceRgb ACCENT_BLUE       = new DeviceRgb(43, 108, 176);
    private static final DeviceRgb GOLD              = new DeviceRgb(200, 169, 81);
    private static final DeviceRgb TEXT_GRAY         = new DeviceRgb(113, 128, 150);
    private static final DeviceRgb WHITE             = new DeviceRgb(255, 255, 255);

    // ── generateCertificate ───────────────────────────────────────────
    @Override
    public String generateCertificate(Long courseId, Long studentId) {
        // Step 1 — Load data
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> enrollmentMap;
        try {
            enrollmentMap = enrollmentClient.getEnrollment(studentId, courseId);
        } catch (Exception e) {
            throw new RuntimeException("Enrollment not found");
        }
        Long enrollmentId = enrollmentMap.get("id") instanceof Number n ? n.longValue() : null;
        Double completionPct = enrollmentMap.get("completionPercentage") instanceof Number n ? n.doubleValue() : null;

        if (completionPct == null || completionPct < 100) {
            throw new RuntimeException("Course not yet completed");
        }

        // Hours spent from lesson progress (via Lesson Service)
        List<Map<String, Object>> progresses = lessonClient.getProgressByEnrollment(enrollmentId);
        int totalSeconds = progresses.stream()
                .mapToInt(lp -> lp.get("timeSpentSeconds") instanceof Number n ? n.intValue() : 0)
                .sum();
        int hoursSpent = Math.round(totalSeconds / 3600f);

        // Best quiz score across all quizzes in this course
        Double quizScore = getBestQuizScore(studentId, courseId);

        // Step 2 — Generate certificate ID if not exists
        String existingCertId = (String) enrollmentMap.get("certificateId");
        if (existingCertId == null) {
            existingCertId = "YBRY-" +
                    LocalDate.now().getYear() + "-" +
                    UUID.randomUUID().toString().substring(0, 4).toUpperCase() + "-" +
                    UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            try {
                enrollmentClient.updateCertificateId(enrollmentId, existingCertId);
            } catch (Exception e) {
                log.warn("[Certificate] Could not persist certificateId: {}", e.getMessage());
            }
        }
        String certId = existingCertId;

        // Step 3 — Resolve real student name
        String studentName = resolveStudentName(studentId);

        // Step 4 — Generate AI remarks
        String aiRemarks = generateAiRemarks(
                course.getTitle(),
                course.getCategory().name(),
                hoursSpent,
                quizScore
        );

        // Step 5 — Generate PDF
        LocalDateTime completedAt = parseDateTimeFromMap(enrollmentMap.get("completedAt"));
        LocalDateTime enrollmentDate = parseDateTimeFromMap(enrollmentMap.get("enrollmentDate"));
        String completionDate = completedAt != null
                ? completedAt.toLocalDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                : enrollmentDate != null
                        ? enrollmentDate.toLocalDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                        : LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        String certDir = uploadDir + "/certificates/";
        new File(certDir).mkdirs();
        String filePath = certDir + "cert_" + studentId + "_" + courseId + ".pdf";

        File existingFile = new File(filePath);
        if (!existingFile.exists() || existingFile.length() == 0) {
            try {
                generatePdf(filePath, course, studentId, studentName, certId, hoursSpent, quizScore, aiRemarks, completionDate);
                log.info("[Certificate] PDF generated for student {} course {}", studentId, courseId);
            } catch (Exception e) {
                log.warn("[Certificate] PDF generation failed: {}", e.getMessage());
            }
        } else {
            log.info("[Certificate] Using cached PDF for student {} course {}", studentId, courseId);
        }

        return certId;
    }

    // ── verifyCertificate ─────────────────────────────────────────────
    @Override
    public VerificationResponseDTO verifyCertificate(String certificateId) {
        Map<String, Object> enrollmentMap;
        try {
            enrollmentMap = enrollmentClient.getByCertificate(certificateId);
        } catch (Exception e) {
            return VerificationResponseDTO.builder()
                    .valid(false)
                    .certificateId(certificateId)
                    .build();
        }

        Long enrollmentId = enrollmentMap.get("id") instanceof Number n ? n.longValue() : null;
        Long studentId = enrollmentMap.get("studentId") instanceof Number n ? n.longValue() : null;
        Long courseId = enrollmentMap.get("courseId") instanceof Number n ? n.longValue() : null;
        Course course = courseId != null ? courseRepository.findById(courseId).orElse(null) : null;

        List<Map<String, Object>> progresses = Collections.emptyList();
        try { progresses = lessonClient.getProgressByEnrollment(enrollmentId); } catch (Exception ignored) {}
        int totalSeconds = progresses.stream()
                .mapToInt(lp -> lp.get("timeSpentSeconds") instanceof Number n ? n.intValue() : 0)
                .sum();
        int hoursSpent = Math.round(totalSeconds / 3600f);

        Double quizScore = course != null ? getBestQuizScore(studentId, course.getId()) : null;

        LocalDateTime completedAt = parseDateTimeFromMap(enrollmentMap.get("completedAt"));
        LocalDateTime enrollmentDate = parseDateTimeFromMap(enrollmentMap.get("enrollmentDate"));
        String completionDate = completedAt != null
                ? completedAt.toLocalDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                : enrollmentDate != null
                        ? enrollmentDate.toLocalDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                        : LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));

        return VerificationResponseDTO.builder()
                .valid(true)
                .certificateId(certificateId)
                .studentName(resolveStudentName(studentId))
                .studentId(studentId)
                .courseTitle(course != null ? course.getTitle() : "Unknown Course")
                .completionDate(completionDate)
                .quizScore(quizScore)
                .hoursSpent(hoursSpent)
                .issuedBy("YBrainy E-Learning Platform")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Double getBestQuizScore(Long studentId, Long courseId) {
        try {
            Map<String, Object> result = quizClient.getBestScore(studentId, courseId);
            if (result != null && result.get("bestScore") instanceof Number) {
                return ((Number) result.get("bestScore")).doubleValue();
            }
        } catch (Exception e) {
            log.warn("[Certificate] Could not get quiz score: {}", e.getMessage());
        }
        return null;
    }

    private String resolveStudentName(Long studentId) {
        try {
            Map<String, Object> user = userClient.getUserById(studentId);
            if (user != null) {
                String firstName = (String) user.getOrDefault("firstName", "");
                String lastName  = (String) user.getOrDefault("lastName", "");
                String fullName  = (firstName + " " + lastName).trim();
                if (!fullName.isEmpty()) return fullName;
            }
        } catch (Exception e) {
            log.warn("[Certificate] Could not resolve name for student {}: {}",
                studentId, e.getMessage());
        }
        return "Student #" + studentId;
    }

    private String generateAiRemarks(String courseTitle, String category, int hoursSpent, Double quizScore) {
        try {
            String prompt = """
                    Write a professional 2-3 sentence certificate remark for a \
                    student who completed the course "%s" in the %s category. \
                    They spent %d hours learning and achieved a quiz score of %.1f%%. \
                    Be encouraging, specific to the subject, and professional. \
                    Return only the remark text, no quotes, no labels.
                    """.formatted(courseTitle, category, hoursSpent, quizScore != null ? quizScore : 0.0);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openRouterKey);
            headers.set("HTTP-Referer", "http://localhost:4301");
            headers.set("X-Title", "YBrainy");

            Map<String, Object> body = Map.of(
                    "model", "google/gemma-3-4b-it:free",
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://openrouter.ai/api/v1/chat/completions", request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText().trim();

        } catch (Exception e) {
            log.warn("AI remarks generation failed: {}", e.getMessage());
            return "This student has demonstrated dedication and commitment in completing this course successfully.";
        }
    }

    private void generatePdf(String filePath, Course course, Long studentId, String studentName,
                              String certId, int hoursSpent, Double quizScore,
                              String aiRemarks, String completionDate) throws Exception {

        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdfDoc = new PdfDocument(writer);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italic  = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        PageSize landscape = PageSize.A4.rotate();
        Document doc = new Document(pdfDoc, landscape);
        doc.setMargins(0, 50, 0, 50);

        // ── PAGE 1 — Main Certificate (Landscape) ─────────────────────

        // Top banner: dark blue with YBrainy title
        Table topBanner = new Table(1).useAllAvailableWidth()
                .setBackgroundColor(PRIMARY_DARK_BLUE)
                .setMarginLeft(-50).setMarginRight(-50);
        topBanner.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph("YBrainy")
                        .setFont(bold).setFontSize(30)
                        .setFontColor(WHITE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(0))
                .add(new Paragraph("E-LEARNING PLATFORM")
                        .setFont(regular).setFontSize(8)
                        .setFontColor(new DeviceRgb(190, 210, 240))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setCharacterSpacing(4f))
                .setPadding(16f));
        doc.add(topBanner);

        // Gold decorative line below header
        doc.add(new Paragraph(" ")
                .setFontSize(2)
                .setBorderBottom(new SolidBorder(GOLD, 2f))
                .setMarginLeft(-50).setMarginRight(-50));

        // Certificate title
        doc.add(new Paragraph("CERTIFICATE OF COMPLETION")
                .setFont(bold).setFontSize(20)
                .setFontColor(PRIMARY_DARK_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setCharacterSpacing(2f)
                .setMarginTop(18f));

        doc.add(new Paragraph("This certifies that")
                .setFont(italic).setFontSize(12)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10f));

        doc.add(new Paragraph(studentName)
                .setFont(bold).setFontSize(26)
                .setFontColor(PRIMARY_DARK_BLUE)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("has successfully completed")
                .setFont(regular).setFontSize(12)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph(course.getTitle())
                .setFont(bold).setFontSize(18)
                .setFontColor(PRIMARY_DARK_BLUE)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER));

        // Category badge
        Paragraph badge = new Paragraph(course.getCategory().name())
                .setFont(bold).setFontSize(9)
                .setFontColor(WHITE)
                .setBackgroundColor(ACCENT_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(4f)
                .setBorderRadius(new BorderRadius(6f))
                .setWidth(UnitValue.createPointValue(100f))
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginTop(6f);
        doc.add(badge);

        doc.add(new Paragraph(" ").setFontSize(6));

        // Gold divider before stats
        doc.add(new Paragraph(" ")
                .setFontSize(2)
                .setBorderBottom(new SolidBorder(GOLD, 1f))
                .setMarginLeft(30).setMarginRight(30));

        doc.add(new Paragraph(" ").setFontSize(4));

        // Stats row
        Table statsTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth();
        statsTable.addCell(createStatCell("COMPLETED", completionDate, bold));
        statsTable.addCell(createStatCell("QUIZ SCORE",
                quizScore != null ? String.format("%.1f%%", quizScore) : "N/A", bold));
        statsTable.addCell(createStatCell("HOURS SPENT", hoursSpent + "h", bold));
        doc.add(statsTable);

        doc.add(new Paragraph(" ").setFontSize(4));

        // Bottom gold line
        doc.add(new Paragraph(" ")
                .setFontSize(2)
                .setBorderBottom(new SolidBorder(GOLD, 1f))
                .setMarginLeft(-50).setMarginRight(-50));

        doc.add(new Paragraph(" ").setFontSize(4));

        // Footer: cert ID + verify URL + issuer
        doc.add(new Paragraph("Certificate ID: " + certId)
                .setFont(regular).setFontSize(8)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("Verify at: " + frontendUrl + "/verify/" + certId)
                .setFont(italic).setFontSize(8)
                .setFontColor(ACCENT_BLUE)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("Issued by YBrainy E-Learning Platform")
                .setFont(regular).setFontSize(8)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginRight(0));

        // Bottom border band
        Table bottomBanner = new Table(1).useAllAvailableWidth()
                .setBackgroundColor(PRIMARY_DARK_BLUE)
                .setMarginLeft(-50).setMarginRight(-50);
        bottomBanner.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph(" ").setFontSize(5))
                .setPadding(4f));
        doc.add(bottomBanner);

        // ── PAGE 2 — AI Remarks (Landscape) ───────────────────────────
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // Header band
        Table p2Header = new Table(1).useAllAvailableWidth()
                .setBackgroundColor(PRIMARY_DARK_BLUE)
                .setMarginLeft(-50).setMarginRight(-50);
        p2Header.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph("ACADEMIC ASSESSMENT")
                        .setFont(bold).setFontSize(18)
                        .setFontColor(WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                .setPadding(20f));
        doc.add(p2Header);

        // Gold line
        doc.add(new Paragraph(" ")
                .setFontSize(2)
                .setBorderBottom(new SolidBorder(GOLD, 2f))
                .setMarginLeft(-50).setMarginRight(-50));

        doc.add(new Paragraph(" ").setFontSize(10));

        doc.add(new Paragraph("Personal Remarks")
                .setFont(bold).setFontSize(18)
                .setFontColor(PRIMARY_DARK_BLUE));

        doc.add(new Paragraph(course.getTitle() + "  ·  " + studentName)
                .setFont(italic).setFontSize(11)
                .setFontColor(TEXT_GRAY));

        doc.add(new Paragraph(" ").setFontSize(8));

        // Opening quote
        doc.add(new Paragraph("\u201C")
                .setFont(bold).setFontSize(48)
                .setFontColor(GOLD)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(-16f));

        // AI remarks text
        doc.add(new Paragraph(aiRemarks)
                .setFont(regular).setFontSize(13)
                .setFontColor(TEXT_GRAY)
                .setMultipliedLeading(1.6f)
                .setMarginLeft(30f)
                .setMarginRight(30f));

        // Closing quote
        doc.add(new Paragraph("\u201D")
                .setFont(bold).setFontSize(48)
                .setFontColor(GOLD)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(-16f));

        doc.add(new Paragraph(" ").setFontSize(10));

        // Footer divider
        doc.add(new Paragraph(" ")
                .setFontSize(2)
                .setBorderBottom(new SolidBorder(GOLD, 1f))
                .setMarginLeft(-50).setMarginRight(-50));

        doc.add(new Paragraph(" ").setFontSize(6));

        doc.add(new Paragraph("Certificate ID: " + certId)
                .setFont(regular).setFontSize(9)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("YBrainy Academic Committee")
                .setFont(bold).setFontSize(10)
                .setFontColor(PRIMARY_DARK_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8f));

        doc.close();
    }

    private LocalDateTime parseDateTimeFromMap(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            try { return LocalDateTime.parse(s); } catch (Exception ignored) {}
        }
        if (value instanceof List<?> arr && arr.size() >= 6) {
            try {
                return LocalDateTime.of(
                    ((Number) arr.get(0)).intValue(), ((Number) arr.get(1)).intValue(),
                    ((Number) arr.get(2)).intValue(), ((Number) arr.get(3)).intValue(),
                    ((Number) arr.get(4)).intValue(), ((Number) arr.get(5)).intValue());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Cell createStatCell(String label, String value, PdfFont bold) {
        return new Cell()
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph(label)
                        .setFont(bold).setFontSize(8)
                        .setFontColor(TEXT_GRAY)
                        .setCharacterSpacing(1f)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(value)
                        .setFont(bold).setFontSize(13)
                        .setFontColor(PRIMARY_DARK_BLUE)
                        .setTextAlignment(TextAlignment.CENTER));
    }
}
