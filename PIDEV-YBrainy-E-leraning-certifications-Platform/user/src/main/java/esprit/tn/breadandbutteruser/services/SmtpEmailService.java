package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.AppMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AppMailProperties mailProperties;

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody, Map<String, Resource> inlineResources) {
        if (!mailProperties.isEnabled()) {
            log.info("Email sending disabled. Skipping email to {}", to);
            return;
        }
        if (!StringUtils.hasText(to)) {
            throw new RuntimeException("Destination email is required");
        }
        if (!StringUtils.hasText(mailProperties.getFromAddress())) {
            throw new RuntimeException("Mail sender address is not configured (app.mail.from-address)");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (StringUtils.hasText(mailProperties.getFromName())) {
                helper.setFrom(new InternetAddress(
                        mailProperties.getFromAddress(),
                        mailProperties.getFromName(),
                        StandardCharsets.UTF_8.name()
                ).toString());
            } else {
                helper.setFrom(mailProperties.getFromAddress());
            }

            if (inlineResources != null) {
                for (Map.Entry<String, Resource> entry : inlineResources.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().exists()) {
                        helper.addInline(entry.getKey(), entry.getValue());
                    }
                }
            }

            mailSender.send(message);
            log.info("Email sent to {} with subject '{}'", to, subject);
        } catch (MessagingException | MailException ex) {
            throw new RuntimeException("Failed to send email to " + to + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to prepare email to " + to + ": " + ex.getMessage(), ex);
        }
    }
}
