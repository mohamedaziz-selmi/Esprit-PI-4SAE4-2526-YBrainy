package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.AppMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailTemplateService {

    private final AppMailProperties mailProperties;

    public String renderForgotPasswordVerificationCode(String recipientName, String verificationCode, long ttlMinutes) {
        return renderTemplate("mail/templates/forgot-password-verification.html", Map.of(
                "{{APP_NAME}}", html(mailProperties.getAppName()),
                "{{RECIPIENT_NAME}}", html(recipientName),
                "{{VERIFICATION_CODE}}", html(verificationCode),
                "{{TTL_MINUTES}}", String.valueOf(ttlMinutes)
        ));
    }

    private String renderTemplate(String classpathPath, Map<String, String> replacements) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue());
            }
            return content;
        } catch (IOException ex) {
            throw new RuntimeException("Unable to load email template: " + classpathPath, ex);
        }
    }

    private String html(String value) {
        return HtmlUtils.htmlEscape(value != null ? value : "");
    }
}
