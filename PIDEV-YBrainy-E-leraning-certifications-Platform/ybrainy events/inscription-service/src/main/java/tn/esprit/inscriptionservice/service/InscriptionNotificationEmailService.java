package tn.esprit.inscriptionservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.esprit.inscriptionservice.dto.EventDto;
import tn.esprit.inscriptionservice.dto.UserDto;
import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class InscriptionNotificationEmailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy - HH:mm");

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String logoPath;

    public InscriptionNotificationEmailService(
            JavaMailSender mailSender,
            @Value("${ybrainy.mail.from:}") String fromAddress,
            @Value("${ybrainy.mail.logo-path:Frontend/src/assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/logos.svg}") String logoPath
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.logoPath = logoPath;
    }

    public void sendDecisionEmail(UserDto student, EventDto event, Inscription inscription, InscriptionStatut status) {
        sendEmail(student, event, inscription, status, false);
    }

    public void sendWaitlistPromotionEmail(UserDto student, EventDto event, Inscription inscription) {
        sendEmail(student, event, inscription, InscriptionStatut.CONFIRMEE, true);
    }

    private void sendEmail(UserDto student, EventDto event, Inscription inscription, InscriptionStatut status, boolean fromWaitlist) {
        if (student == null || student.email() == null || student.email().isBlank()) {
            log.warn("Skipping inscription email because student email is missing. studentId={}",
                    student != null ? student.userId() : -1);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(student.email());
            helper.setSubject(buildSubject(status, event, fromWaitlist));
            boolean hasInlineLogo = attachInlineLogo(helper);
            helper.setText(buildHtml(student, event, inscription, status, hasInlineLogo, fromWaitlist), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException | RuntimeException ex) {
            log.warn("Failed to send inscription decision email for inscriptionId={}: {}",
                    inscription != null ? inscription.getIdInscription() : -1, ex.getMessage());
        }
    }

    private String buildSubject(InscriptionStatut status, EventDto event, boolean fromWaitlist) {
        String eventName = event != null && event.name() != null ? event.name() : "your event";
        if (fromWaitlist) {
            return "YBrainy - A spot opened for " + eventName;
        }
        return (status == InscriptionStatut.CONFIRMEE
                ? "YBrainy - Registration confirmed for "
                : "YBrainy - Update about your registration for ") + eventName;
    }

    private String buildHtml(UserDto student, EventDto event, Inscription inscription, InscriptionStatut status, boolean hasInlineLogo, boolean fromWaitlist) {
        String studentName = buildStudentName(student);
        String eventName = safe(event != null ? event.name() : null, "Event");
        String eventRef = safe(event != null ? event.referenceEvent() : null, "N/A");
        String eventType = safe(event != null ? event.type() : null, "EVENT");
        String eventLocation = safe(event != null ? event.location() : null, "Online / TBD");
        String eventStart = formatDateTime(event != null ? event.dateDebut() : null);
        String eventEnd = formatDateTime(event != null ? event.dateFin() : null);
        String decisionLabel = status == InscriptionStatut.CONFIRMEE ? "Confirmed" : "Refused";
        String decisionColor = status == InscriptionStatut.CONFIRMEE ? "#2EBA7B" : "#F26B5E";
        String decisionSurface = status == InscriptionStatut.CONFIRMEE ? "#EAF9F1" : "#FFF0EC";
        String decisionText = status == InscriptionStatut.CONFIRMEE
                ? (fromWaitlist
                ? "A seat has opened up and you have been automatically moved from the waiting list to confirmed registration. Your QR pass is ready below."
                : "Your inscription has been approved by the YBrainy team. Your QR pass is ready below.")
                : "Your inscription could not be approved this time. You can still explore upcoming events on YBrainy.";
        boolean isConfirmed = status == InscriptionStatut.CONFIRMEE;
        String headerBackground = isConfirmed
                ? "#EEF3FF"
                : "#FFF6F2";
        String headerTitleColor = "#243166";
        String headerBodyColor = "#5E6887";
        String logoMarkup = hasInlineLogo
                ? "<img src=\"cid:ybrainyLogo\" alt=\"YBrainy\" style=\"display:block;height:42px;width:auto;\">"
                : "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td style=\"width:44px;height:44px;border-radius:14px;background:#0F73EE;text-align:center;vertical-align:middle;font-size:20px;font-weight:800;color:#ffffff;\">Y</td><td style=\"padding-left:12px;font-size:28px;line-height:1;font-weight:800;color:"
                + headerTitleColor + ";\">YBrainy</td></tr></table>";

        String qrPayload = """
                YBrainy Event Pass
                Reference : %s
                Event     : %s
                Type      : %s
                Start     : %s
                End       : %s
                Location  : %s
                Student   : %s
                Decision  : %s
                """.formatted(eventRef, eventName, eventType, eventStart, eventEnd, eventLocation, studentName, decisionLabel);
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=12&data="
                + URLEncoder.encode(qrPayload, StandardCharsets.UTF_8);

        String detailsBlock = isConfirmed
                ? """
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                      <tr>
                        <td width="50%%" style="padding:0 6px 10px 0;">%s</td>
                        <td width="50%%" style="padding:0 0 10px 6px;">%s</td>
                      </tr>
                      <tr>
                        <td width="50%%" style="padding:0 6px 10px 0;">%s</td>
                        <td width="50%%" style="padding:0 0 10px 6px;">%s</td>
                      </tr>
                      <tr>
                        <td width="50%%" style="padding:0 6px 0 0;">%s</td>
                        <td width="50%%" style="padding:0 0 0 6px;">%s</td>
                      </tr>
                    </table>
                    """.formatted(
                infoCard("Event Type", eventType),
                infoCard("Location", eventLocation),
                infoCard("Starts", eventStart),
                infoCard("Ends", eventEnd),
                infoCard("Reference", eventRef),
                infoCard("Decision", inscription != null && inscription.getStatut() != null ? inscription.getStatut().name() : "N/A")
        )
                : """
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                      <tr>
                        <td width="100%%" style="padding:0;">%s</td>
                      </tr>
                    </table>
                    """.formatted(
                infoCard("Decision", inscription != null && inscription.getStatut() != null ? inscription.getStatut().name() : "N/A")
        );

        String qrBlock = isConfirmed
                ? """
                    <td width="38%%" valign="top" style="padding-left:10px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(180deg,#f9fbff 0%%,#f2f6ff 100%%);border:1px solid rgba(115,128,179,0.14);border-radius:24px;">
                        <tr>
                          <td align="center" style="padding:18px;">
                            <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">QR Access</div>
                            <div style="height:12px;"></div>
                            <img src="%s" alt="Event QR code" width="190" height="190" style="display:block;width:190px;height:190px;max-width:190px;border-radius:18px;background:#ffffff;padding:10px;border:1px solid rgba(115,128,179,0.12);">
                            <div style="height:12px;"></div>
                            <div style="font-size:13px;line-height:1.65;color:#6f7897;">Scan this QR for a clean summary of your confirmed YBrainy event pass.</div>
                            <div style="height:12px;"></div>
                            <a href="%s" style="display:inline-block;padding:10px 16px;border-radius:999px;background:#4d47d9;color:#ffffff;text-decoration:none;font-size:13px;font-weight:800;">Open QR</a>
                          </td>
                        </tr>
                      </table>
                    </td>
                    """.formatted(qrUrl, qrUrl)
                : "";

        String leftColumnWidth = isConfirmed ? "62%%" : "100%%";
        String eventReferenceBlock = isConfirmed
                ? """
                    <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">Event Reference</div>
                    <div style="height:8px;"></div>
                    <div style="font-size:22px;font-weight:800;color:#202a4c;">%s</div>
                    """.formatted(escapeHtml(eventRef))
                : "";
        String eventCardTopSpacing = isConfirmed ? "<div style=\"height:16px;\"></div>" : "";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#eef3ff;font-family:Segoe UI,Arial,sans-serif;color:#1f2a44;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#eef3ff;">
                    <tr>
                      <td align="center" style="padding:28px 14px;">
                        <table role="presentation" width="680" cellpadding="0" cellspacing="0" border="0" style="width:680px;max-width:680px;background:#ffffff;border-radius:28px;overflow:hidden;box-shadow:0 18px 48px rgba(16,24,52,0.12);">
                          <tr>
                            <td style="padding:30px 32px;background:%s;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td align="left">
                                    %s
                                  </td>
                                </tr>
                                <tr><td style="height:20px;"></td></tr>
                                <tr>
                                  <td>
                                    <span style="display:inline-block;padding:8px 13px;border-radius:999px;background:%s;font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:%s;">Registration %s</span>
                                  </td>
                                </tr>
                                <tr><td style="height:18px;"></td></tr>
                                <tr>
                                  <td style="font-size:30px;line-height:1.14;font-weight:800;color:%s;">%s</td>
                                </tr>
                                <tr><td style="height:10px;"></td></tr>
                                <tr>
                                  <td style="font-size:15px;line-height:1.75;color:%s;">%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <tr>
                            <td style="padding:28px 32px 32px;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td style="padding:0 0 16px 0;">
                                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f7f9ff;border:1px solid rgba(115,128,179,0.14);border-radius:22px;">
                                      <tr>
                                        <td style="padding:18px 20px;">
                                          <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">Student</div>
                                          <div style="height:8px;"></div>
                                          <div style="font-size:20px;font-weight:800;color:#202a4c;">%s</div>
                                          <div style="height:6px;"></div>
                                          <div style="font-size:14px;line-height:1.6;color:#6c7593;">%s</div>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>

                                <tr>
                                  <td>
                                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                      <tr>
                                        <td width="%s" valign="top" style="padding-right:%s;">
                                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#ffffff;border:1px solid rgba(115,128,179,0.14);border-radius:22px;box-shadow:0 12px 28px rgba(15,26,58,0.05);">
                                            <tr>
                                              <td style="padding:20px;">
                                                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                                                  <tr>
                                                    <td valign="top">
                                                      %s
                                                    </td>
                                                    <td align="right" valign="top">
                                                      <span style="display:inline-block;padding:8px 12px;border-radius:999px;background:%s;color:%s;font-size:11px;font-weight:800;text-transform:uppercase;">%s</span>
                                                    </td>
                                                  </tr>
                                                </table>
                                                %s
                                                <div style="font-size:24px;font-weight:800;line-height:1.2;color:#1f2947;">%s</div>
                                                <div style="height:14px;"></div>
                                                %s
                                              </td>
                                            </tr>
                                          </table>
                                        </td>

                                        %s
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                headerBackground,
                logoMarkup,
                isConfirmed ? "#E3EAFE" : "#FDE7E0",
                isConfirmed ? "#3556D8" : "#E05C4F",
                decisionLabel,
                headerTitleColor,
                status == InscriptionStatut.CONFIRMEE
                        ? (fromWaitlist ? "You are registered from the waiting list" : "Your registration is confirmed")
                        : "Your registration was not accepted",
                headerBodyColor,
                decisionText,
                studentName,
                safe(student.email(), ""),
                leftColumnWidth,
                isConfirmed ? "10px" : "0",
                eventReferenceBlock,
                decisionSurface,
                decisionColor,
                decisionLabel,
                eventCardTopSpacing,
                eventName,
                detailsBlock,
                qrBlock
        );
    }

    private String infoCard(String label, String value) {
        return """
                <div style="padding:14px 15px;border-radius:16px;background:#f7f9ff;border:1px solid rgba(115,128,179,0.12);">
                  <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#8590af;margin-bottom:6px;">%s</div>
                  <div style="font-size:14px;font-weight:700;color:#23305a;line-height:1.5;">%s</div>
                </div>
                """.formatted(escapeHtml(label), escapeHtml(value));
    }

    private String buildStudentName(UserDto student) {
        if (student == null) return "Student";
        String fullName = (safe(student.firstName(), "") + " " + safe(student.lastName(), "")).trim();
        return fullName.isBlank() ? "Student #" + student.userId() : fullName;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "N/A" : value.format(DATE_TIME_FORMATTER);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String escapeHtml(String value) {
        return safe(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean attachInlineLogo(MimeMessageHelper helper) {
        Path path = resolveLogoPath();
        if (path == null) {
            return false;
        }

        try {
            helper.addInline("ybrainyLogo", new FileSystemResource(path), "image/svg+xml");
            return true;
        } catch (MessagingException ex) {
            log.warn("Unable to inline YBrainy logo from {}: {}", path, ex.getMessage());
            return false;
        }
    }

    private Path resolveLogoPath() {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }

        Path configured = Paths.get(logoPath);
        if (Files.exists(configured)) {
            return configured;
        }

        Path fromWorkingDir = Paths.get(System.getProperty("user.dir", "")).resolve(logoPath).normalize();
        if (Files.exists(fromWorkingDir)) {
            return fromWorkingDir;
        }

        return null;
    }
}

//import jakarta.mail.MessagingException;
//import jakarta.mail.internet.MimeMessage;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.FileSystemResource;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.stereotype.Service;
//import tn.esprit.inscriptionservice.dto.EventDto;
//import tn.esprit.inscriptionservice.dto.UserDto;
//import tn.esprit.inscriptionservice.entity.Inscription;
//import tn.esprit.inscriptionservice.entity.InscriptionStatut;
//
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//@Service
//@Slf4j
//public class InscriptionNotificationEmailService {
//
//    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy - HH:mm");
//
//    private final JavaMailSender mailSender;
//    private final String fromAddress;
//    private final String logoPath;
//
//    public InscriptionNotificationEmailService(
//            JavaMailSender mailSender,
//            @Value("${ybrainy.mail.from:}") String fromAddress,
//            @Value("${ybrainy.mail.logo-path:Frontend/src/assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/logos.svg}") String logoPath
//    ) {
//        this.mailSender = mailSender;
//        this.fromAddress = fromAddress;
//        this.logoPath = logoPath;
//    }
//
//    public void sendDecisionEmail(UserDto student, EventDto event, Inscription inscription, InscriptionStatut status) {
//        if (student == null || student.email() == null || student.email().isBlank()) {
//            log.warn("Skipping inscription email because student email is missing. studentId={}",
//                    student != null ? student.userId() : -1);
//            return;
//        }
//
//        try {
//            MimeMessage mimeMessage = mailSender.createMimeMessage();
//            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
//            if (fromAddress != null && !fromAddress.isBlank()) {
//                helper.setFrom(fromAddress);
//            }
//            helper.setTo(student.email());
//            helper.setSubject(buildSubject(status, event));
//            boolean hasInlineLogo = attachInlineLogo(helper);
//            helper.setText(buildHtml(student, event, inscription, status, hasInlineLogo), true);
//            mailSender.send(mimeMessage);
//        } catch (MessagingException | RuntimeException ex) {
//            log.warn("Failed to send inscription decision email for inscriptionId={}: {}",
//                    inscription != null ? inscription.getIdInscription() : -1, ex.getMessage());
//        }
//    }
//
//    private String buildSubject(InscriptionStatut status, EventDto event) {
//        String eventName = event != null && event.name() != null ? event.name() : "your event";
//        return (status == InscriptionStatut.CONFIRMEE
//                ? "YBrainy - Registration confirmed for "
//                : "YBrainy - Update about your registration for ") + eventName;
//    }
//
//    private String buildHtml(UserDto student, EventDto event, Inscription inscription, InscriptionStatut status, boolean hasInlineLogo) {
//        String studentName = buildStudentName(student);
//        String eventName = safe(event != null ? event.name() : null, "Event");
//        String eventRef = safe(event != null ? event.referenceEvent() : null, "N/A");
//        String eventType = safe(event != null ? event.type() : null, "EVENT");
//        String eventLocation = safe(event != null ? event.location() : null, "Online / TBD");
//        String eventStart = formatDateTime(event != null ? event.dateDebut() : null);
//        String eventEnd = formatDateTime(event != null ? event.dateFin() : null);
//        String decisionLabel = status == InscriptionStatut.CONFIRMEE ? "Confirmed" : "Refused";
//        String decisionColor = status == InscriptionStatut.CONFIRMEE ? "#2EBA7B" : "#F26B5E";
//        String decisionSurface = status == InscriptionStatut.CONFIRMEE ? "#EAF9F1" : "#FFF0EC";
//        String decisionText = status == InscriptionStatut.CONFIRMEE
//                ? "Your inscription has been approved by the YBrainy team. Your QR pass is ready below."
//                : "Your inscription could not be approved this time. You can still explore upcoming events on YBrainy.";
//        boolean isConfirmed = status == InscriptionStatut.CONFIRMEE;
//        String headerBackground = isConfirmed
//                ? "#EEF3FF"
//                : "#FFF6F2";
//        String headerTitleColor = "#243166";
//        String headerBodyColor = "#5E6887";
//        String logoMarkup = hasInlineLogo
//                ? "<img src=\"cid:ybrainyLogo\" alt=\"YBrainy\" style=\"display:block;height:42px;width:auto;\">"
//                : "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td style=\"width:44px;height:44px;border-radius:14px;background:#0F73EE;text-align:center;vertical-align:middle;font-size:20px;font-weight:800;color:#ffffff;\">Y</td><td style=\"padding-left:12px;font-size:28px;line-height:1;font-weight:800;color:"
//                + headerTitleColor + ";\">YBrainy</td></tr></table>";
//
//        String qrPayload = """
//                YBrainy Event Pass
//                Reference : %s
//                Event     : %s
//                Type      : %s
//                Start     : %s
//                End       : %s
//                Location  : %s
//                Student   : %s
//                Decision  : %s
//                """.formatted(eventRef, eventName, eventType, eventStart, eventEnd, eventLocation, studentName, decisionLabel);
//        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=12&data="
//                + URLEncoder.encode(qrPayload, StandardCharsets.UTF_8);
//
//        String detailsBlock = isConfirmed
//                ? """
//                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                      <tr>
//                        <td width="50%%" style="padding:0 6px 10px 0;">%s</td>
//                        <td width="50%%" style="padding:0 0 10px 6px;">%s</td>
//                      </tr>
//                      <tr>
//                        <td width="50%%" style="padding:0 6px 10px 0;">%s</td>
//                        <td width="50%%" style="padding:0 0 10px 6px;">%s</td>
//                      </tr>
//                      <tr>
//                        <td width="50%%" style="padding:0 6px 0 0;">%s</td>
//                        <td width="50%%" style="padding:0 0 0 6px;">%s</td>
//                      </tr>
//                    </table>
//                    """.formatted(
//                infoCard("Event Type", eventType),
//                infoCard("Location", eventLocation),
//                infoCard("Starts", eventStart),
//                infoCard("Ends", eventEnd),
//                infoCard("Reference", eventRef),
//                infoCard("Decision", inscription != null && inscription.getStatut() != null ? inscription.getStatut().name() : "N/A")
//        )
//                : """
//                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                      <tr>
//                        <td width="100%%" style="padding:0;">%s</td>
//                      </tr>
//                    </table>
//                    """.formatted(
//                infoCard("Decision", inscription != null && inscription.getStatut() != null ? inscription.getStatut().name() : "N/A")
//        );
//
//        String qrBlock = isConfirmed
//                ? """
//                    <td width="38%%" valign="top" style="padding-left:10px;">
//                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(180deg,#f9fbff 0%%,#f2f6ff 100%%);border:1px solid rgba(115,128,179,0.14);border-radius:24px;">
//                        <tr>
//                          <td align="center" style="padding:18px;">
//                            <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">QR Access</div>
//                            <div style="height:12px;"></div>
//                            <img src="%s" alt="Event QR code" width="190" height="190" style="display:block;width:190px;height:190px;max-width:190px;border-radius:18px;background:#ffffff;padding:10px;border:1px solid rgba(115,128,179,0.12);">
//                            <div style="height:12px;"></div>
//                            <div style="font-size:13px;line-height:1.65;color:#6f7897;">Scan this QR for a clean summary of your confirmed YBrainy event pass.</div>
//                            <div style="height:12px;"></div>
//                            <a href="%s" style="display:inline-block;padding:10px 16px;border-radius:999px;background:#4d47d9;color:#ffffff;text-decoration:none;font-size:13px;font-weight:800;">Open QR</a>
//                          </td>
//                        </tr>
//                      </table>
//                    </td>
//                    """.formatted(qrUrl, qrUrl)
//                : "";
//
//        String leftColumnWidth = isConfirmed ? "62%%" : "100%%";
//        String eventReferenceBlock = isConfirmed
//                ? """
//                    <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">Event Reference</div>
//                    <div style="height:8px;"></div>
//                    <div style="font-size:22px;font-weight:800;color:#202a4c;">%s</div>
//                    """.formatted(escapeHtml(eventRef))
//                : "";
//        String eventCardTopSpacing = isConfirmed ? "<div style=\"height:16px;\"></div>" : "";
//
//        return """
//                <!DOCTYPE html>
//                <html lang="en">
//                <body style="margin:0;padding:0;background:#eef3ff;font-family:Segoe UI,Arial,sans-serif;color:#1f2a44;">
//                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#eef3ff;">
//                    <tr>
//                      <td align="center" style="padding:28px 14px;">
//                        <table role="presentation" width="680" cellpadding="0" cellspacing="0" border="0" style="width:680px;max-width:680px;background:#ffffff;border-radius:28px;overflow:hidden;box-shadow:0 18px 48px rgba(16,24,52,0.12);">
//                          <tr>
//                            <td style="padding:30px 32px;background:%s;">
//                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                                <tr>
//                                  <td align="left">
//                                    %s
//                                  </td>
//                                </tr>
//                                <tr><td style="height:20px;"></td></tr>
//                                <tr>
//                                  <td>
//                                    <span style="display:inline-block;padding:8px 13px;border-radius:999px;background:%s;font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:%s;">Registration %s</span>
//                                  </td>
//                                </tr>
//                                <tr><td style="height:18px;"></td></tr>
//                                <tr>
//                                  <td style="font-size:30px;line-height:1.14;font-weight:800;color:%s;">%s</td>
//                                </tr>
//                                <tr><td style="height:10px;"></td></tr>
//                                <tr>
//                                  <td style="font-size:15px;line-height:1.75;color:%s;">%s</td>
//                                </tr>
//                              </table>
//                            </td>
//                          </tr>
//
//                          <tr>
//                            <td style="padding:28px 32px 32px;">
//                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                                <tr>
//                                  <td style="padding:0 0 16px 0;">
//                                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f7f9ff;border:1px solid rgba(115,128,179,0.14);border-radius:22px;">
//                                      <tr>
//                                        <td style="padding:18px 20px;">
//                                          <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7f8aac;">Student</div>
//                                          <div style="height:8px;"></div>
//                                          <div style="font-size:20px;font-weight:800;color:#202a4c;">%s</div>
//                                          <div style="height:6px;"></div>
//                                          <div style="font-size:14px;line-height:1.6;color:#6c7593;">%s</div>
//                                        </td>
//                                      </tr>
//                                    </table>
//                                  </td>
//                                </tr>
//
//                                <tr>
//                                  <td>
//                                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                                      <tr>
//                                        <td width="%s" valign="top" style="padding-right:%s;">
//                                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#ffffff;border:1px solid rgba(115,128,179,0.14);border-radius:22px;box-shadow:0 12px 28px rgba(15,26,58,0.05);">
//                                            <tr>
//                                              <td style="padding:20px;">
//                                                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
//                                                  <tr>
//                                                    <td valign="top">
//                                                      %s
//                                                    </td>
//                                                    <td align="right" valign="top">
//                                                      <span style="display:inline-block;padding:8px 12px;border-radius:999px;background:%s;color:%s;font-size:11px;font-weight:800;text-transform:uppercase;">%s</span>
//                                                    </td>
//                                                  </tr>
//                                                </table>
//                                                %s
//                                                <div style="font-size:24px;font-weight:800;line-height:1.2;color:#1f2947;">%s</div>
//                                                <div style="height:14px;"></div>
//                                                %s
//                                              </td>
//                                            </tr>
//                                          </table>
//                                        </td>
//
//                                        %s
//                                      </tr>
//                                    </table>
//                                  </td>
//                                </tr>
//                              </table>
//                            </td>
//                          </tr>
//                        </table>
//                      </td>
//                    </tr>
//                  </table>
//                </body>
//                </html>
//                """.formatted(
//                headerBackground,
//                logoMarkup,
//                isConfirmed ? "#E3EAFE" : "#FDE7E0",
//                isConfirmed ? "#3556D8" : "#E05C4F",
//                decisionLabel,
//                headerTitleColor,
//                status == InscriptionStatut.CONFIRMEE ? "Your registration is confirmed" : "Your registration was not accepted",
//                headerBodyColor,
//                decisionText,
//                studentName,
//                safe(student.email(), ""),
//                leftColumnWidth,
//                isConfirmed ? "10px" : "0",
//                eventReferenceBlock,
//                decisionSurface,
//                decisionColor,
//                decisionLabel,
//                eventCardTopSpacing,
//                eventName,
//                detailsBlock,
//                qrBlock
//        );
//    }
//
//    private String infoCard(String label, String value) {
//        return """
//                <div style="padding:14px 15px;border-radius:16px;background:#f7f9ff;border:1px solid rgba(115,128,179,0.12);">
//                  <div style="font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#8590af;margin-bottom:6px;">%s</div>
//                  <div style="font-size:14px;font-weight:700;color:#23305a;line-height:1.5;">%s</div>
//                </div>
//                """.formatted(escapeHtml(label), escapeHtml(value));
//    }
//
//    private String buildStudentName(UserDto student) {
//        if (student == null) return "Student";
//        String fullName = (safe(student.prenom(), "") + " " + safe(student.nom(), "")).trim();
//        return fullName.isBlank() ? "Student #" + student.idUser() : fullName;
//    }
//
//    private String formatDateTime(LocalDateTime value) {
//        return value == null ? "N/A" : value.format(DATE_TIME_FORMATTER);
//    }
//
//    private String safe(String value, String fallback) {
//        return value == null || value.isBlank() ? fallback : value;
//    }
//
//    private String escapeHtml(String value) {
//        return safe(value, "")
//                .replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;")
//                .replace("\"", "&quot;")
//                .replace("'", "&#39;");
//    }
//
//    private boolean attachInlineLogo(MimeMessageHelper helper) {
//        Path path = resolveLogoPath();
//        if (path == null) {
//            return false;
//        }
//
//        try {
//            helper.addInline("ybrainyLogo", new FileSystemResource(path), "image/svg+xml");
//            return true;
//        } catch (MessagingException ex) {
//            log.warn("Unable to inline YBrainy logo from {}: {}", path, ex.getMessage());
//            return false;
//        }
//    }
//
//    private Path resolveLogoPath() {
//        if (logoPath == null || logoPath.isBlank()) {
//            return null;
//        }
//
//        Path configured = Paths.get(logoPath);
//        if (Files.exists(configured)) {
//            return configured;
//        }
//
//        Path fromWorkingDir = Paths.get(System.getProperty("user.dir", "")).resolve(logoPath).normalize();
//        if (Files.exists(fromWorkingDir)) {
//            return fromWorkingDir;
//        }
//
//        return null;
//    }
//}