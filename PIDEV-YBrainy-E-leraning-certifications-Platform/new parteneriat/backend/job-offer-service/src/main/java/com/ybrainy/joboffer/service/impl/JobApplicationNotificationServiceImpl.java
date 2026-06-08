package com.ybrainy.joboffer.service.impl;

import com.ybrainy.joboffer.entity.JobApplication;
import com.ybrainy.joboffer.service.JobApplicationNotificationService;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class JobApplicationNotificationServiceImpl implements JobApplicationNotificationService {

  private static final Logger log = LoggerFactory.getLogger(JobApplicationNotificationServiceImpl.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final String fromAddress;
  private final String mailUsername;
  private final boolean acceptedNotificationEnabled;
  private final String mailHost;

  public JobApplicationNotificationServiceImpl(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.notifications.from:no-reply@ybrainy.local}") String fromAddress,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${app.notifications.accepted.enabled:true}") boolean acceptedNotificationEnabled,
      @Value("${spring.mail.host:}") String mailHost) {
    this.mailSenderProvider = mailSenderProvider;
    this.fromAddress = fromAddress;
    this.mailUsername = mailUsername;
    this.acceptedNotificationEnabled = acceptedNotificationEnabled;
    this.mailHost = mailHost;
  }

  @Override
  public boolean notifyAccepted(JobApplication application, String offerTitle) {
    if (!acceptedNotificationEnabled) {
      log.info("Acceptance notification skipped (disabled by config) for application {}", application.getId());
      return false;
    }

    if (mailHost == null || mailHost.isBlank()) {
      log.warn("Acceptance notification skipped (spring.mail.host is not configured) for application {}", application.getId());
      return false;
    }

    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null) {
      log.warn("Notification skipped (mail sender not configured) for application {}", application.getId());
      return false;
    }

    String to = application.getApplicantEmail();
    if (to == null || to.isBlank()) {
      log.warn("Notification skipped (missing applicant email) for application {}", application.getId());
      return false;
    }

    try {
      String subject = buildSubject(offerTitle);
      String displayName = resolveDisplayName(application.getApplicantName());
      String safeOfferTitle = normalizeOfferTitle(offerTitle);
      String htmlBody = buildAcceptedHtmlBody(displayName, safeOfferTitle);
      String plainBody = buildAcceptedPlainBody(displayName, safeOfferTitle);
      sendHtmlWithFallback(mailSender, to.trim(), subject, htmlBody, plainBody);
      log.info("Acceptance notification sent to {} for application {}", to.trim(), application.getId());
      return true;
    } catch (Exception ex) {
      log.error("Failed to send acceptance notification to {}", to, ex);
      return false;
    }
  }

  private String resolveFromAddress() {
    String configured = fromAddress == null ? "" : fromAddress.trim();
    String username = mailUsername == null ? "" : mailUsername.trim();
    if (configured.isBlank()) {
      return username.isBlank() ? "no-reply@ybrainy.local" : username;
    }
    if (configured.endsWith("@ybrainy.local") && !username.isBlank()) {
      return username;
    }
    return configured;
  }

  private void sendHtmlWithFallback(
      JavaMailSender mailSender, String to, String subject, String htmlBody, String plainBody) {
    try {
      var mimeMessage = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, "UTF-8");
      helper.setFrom(resolveFromAddress());
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlBody, true); // IMPORTANT !!!
      mailSender.send(mimeMessage);
    } catch (Exception htmlEx) {
      log.warn("HTML notification failed for {}. Falling back to plain text.", to, htmlEx);
      SimpleMailMessage fallback = new SimpleMailMessage();
      fallback.setFrom(resolveFromAddress());
      fallback.setTo(to);
      fallback.setSubject(subject);
      fallback.setText(plainBody);
      mailSender.send(fallback);
    }
  }

  private String buildSubject(String offerTitle) {
    return "Application Accepted - " + normalizeOfferTitle(offerTitle);
  }

  private String resolveDisplayName(String applicantName) {
    return applicantName == null || applicantName.isBlank() ? "Candidate" : applicantName.trim();
  }

  private String normalizeOfferTitle(String offerTitle) {
    return offerTitle == null || offerTitle.isBlank() ? "Offre d'emploi" : offerTitle.trim();
  }

  private String buildAcceptedPlainBody(String applicantName, String offerTitle) {
    return "Hello " + applicantName + ",\n\n"
        + "Great news! Your application for \"" + offerTitle + "\" has been accepted.\n"
        + "Our team will contact you soon with the next steps.\n\n"
        + "Best regards,\n"
        + "YBrainy Team";
  }

  private String buildAcceptedHtmlBody(String applicantName, String offerTitle) {
    String safeName = HtmlUtils.htmlEscape(applicantName);
    String safeOffer = HtmlUtils.htmlEscape(offerTitle);
    int year = Year.now().getValue();

    return """
        <!DOCTYPE html>
        <html>
        <body style="margin:0; padding:0; background-color:#f4f6f8; font-family:Arial, sans-serif;">

        <table width="100%%" cellpadding="0" cellspacing="0" style="padding:20px 0;">
          <tr>
            <td align="center">

              <!-- Container -->
              <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 10px 25px rgba(0,0,0,0.1);">

                <!-- HEADER -->
                <tr>
                  <td style="background:linear-gradient(135deg,#4f46e5,#7c3aed); padding:20px; text-align:center; color:white;">

                    <!-- LOGO -->
                    <img src="https://your-domain.com/logo.png" width="120" style="margin-bottom:10px;" alt="YBrainy Logo" />

                    <h2 style="margin:0;">YBrainy</h2>
                    <p style="margin:5px 0 0;">&#127881; Application Accepted</p>
                  </td>
                </tr>

                <!-- CONTENT -->
                <tr>
                  <td style="padding:30px; color:#333;">

                    <p style="font-size:16px;">Hello <strong>%s</strong>,</p>

                    <p style="font-size:15px; line-height:1.6;">
                      Great news! Your application for
                      <strong style="color:#4f46e5;">%s</strong> has been accepted.
                    </p>

                    <p style="font-size:15px; line-height:1.6;">
                      Our team will contact you soon with the next steps.
                    </p>

                    <!-- BUTTON -->
                    <div style="text-align:center; margin:30px 0;">
                      <a href="http://localhost:4200/applications"
                         style="background:linear-gradient(135deg,#4f46e5,#7c3aed);
                                color:white;
                                padding:12px 25px;
                                text-decoration:none;
                                border-radius:8px;
                                font-weight:bold;
                                display:inline-block;">
                        View My Applications &#128640;
                      </a>
                    </div>

                    <p style="margin-top:30px;">
                      Best regards,<br>
                      <strong>YBrainy Team</strong>
                    </p>

                  </td>
                </tr>

                <!-- FOOTER -->
                <tr>
                  <td style="background:#f9fafb; text-align:center; padding:15px; font-size:12px; color:#888;">
                    &copy; %d YBrainy &mdash; All rights reserved
                  </td>
                </tr>

              </table>

            </td>
          </tr>
        </table>

        </body>
        </html>
        """
        .formatted(safeName, safeOffer, year);
  }
}
