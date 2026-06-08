package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.AppMailProperties;
import esprit.tn.breadandbutteruser.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordVerificationService {
    private static final String LOGO_CONTENT_ID = "appLogo";

    private final EmailService emailService;
    private final MailTemplateService mailTemplateService;
    private final AppMailProperties mailProperties;
    @Value("${app.mail.dev-fallback-on-failure:true}")
    private boolean devFallbackOnFailure;
    @Value("${app.mail.dev-expose-fallback-code:true}")
    private boolean devExposeFallbackCode;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, VerificationCodeEntry> codeStore = new ConcurrentHashMap<>();

    public VerificationDispatchResult sendVerificationCode(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new RuntimeException("User email is required for forgot-password");
        }

        AppMailProperties.ForgotPassword cfg = mailProperties.getForgotPassword();
        boolean mailEnabled = mailProperties.isEnabled();
        int codeLength = Math.max(cfg.getCodeLength(), 4);
        long ttlMinutes = Math.max(cfg.getCodeTtlMinutes(), 1L);
        String verificationCode = generateNumericCode(codeLength);
        Instant expiresAt = Instant.now().plusSeconds(ttlMinutes * 60);

        codeStore.put(normalizeEmail(user.getEmail()), new VerificationCodeEntry(verificationCode, expiresAt));

        String displayName = resolveDisplayName(user);
        String html = mailTemplateService.renderForgotPasswordVerificationCode(displayName, verificationCode, ttlMinutes);
        Resource logo = new ClassPathResource(mailProperties.getLogoClasspath());
        try {
            emailService.sendHtmlEmail(
                    user.getEmail(),
                    cfg.getSubject(),
                    html,
                    Map.of(LOGO_CONTENT_ID, logo)
            );
            if (mailEnabled) {
                log.info("Forgot-password verification code sent to {} (expires at {})", user.getEmail(), expiresAt);
                return new VerificationDispatchResult(DeliveryMode.EMAIL, null);
            } else {
                log.warn("DEV MAIL DISABLED forgot-password code for {} is {} (expires at {})",
                        user.getEmail(), verificationCode, expiresAt);
                return new VerificationDispatchResult(
                        DeliveryMode.DEV_FALLBACK,
                        devExposeFallbackCode ? verificationCode : null
                );
            }
        } catch (RuntimeException ex) {
            // Keep a dev fallback in logs, but surface the error back to the client so the UI does not
            // incorrectly claim the email was sent when SMTP is misconfigured.
            log.error("Forgot-password email delivery failed for {}: {}", user.getEmail(), ex.getMessage());
            log.warn("DEV FALLBACK forgot-password code for {} is {} (expires at {})",
                    user.getEmail(), verificationCode, expiresAt);
            if (devFallbackOnFailure) {
                return new VerificationDispatchResult(
                        DeliveryMode.DEV_FALLBACK,
                        devExposeFallbackCode ? verificationCode : null
                );
            }
            throw new RuntimeException("Email service temporarily unavailable: SMTP authentication failed");
        }
    }

    public boolean verifyCode(String email, String code) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            return false;
        }
        VerificationCodeEntry entry = codeStore.get(normalizeEmail(email));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            codeStore.remove(normalizeEmail(email));
            return false;
        }
        boolean matches = entry.code().equals(code.trim());
        if (matches) {
            codeStore.remove(normalizeEmail(email));
        }
        return matches;
    }

    private String resolveDisplayName(User user) {
        if (StringUtils.hasText(user.getFirstName())) {
            return user.getFirstName();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "there";
    }

    private String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    public enum DeliveryMode {
        EMAIL,
        DEV_FALLBACK
    }

    public record VerificationDispatchResult(DeliveryMode deliveryMode, String fallbackCode) {
    }

    private record VerificationCodeEntry(String code, Instant expiresAt) {
    }
}
