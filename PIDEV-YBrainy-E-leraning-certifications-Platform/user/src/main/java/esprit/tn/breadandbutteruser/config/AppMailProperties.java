package esprit.tn.breadandbutteruser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {
    private boolean enabled = true;
    private String fromAddress;
    private String fromName = "ybrainy";
    private String appName = "ybrainy";
    private String logoClasspath = "mail/assets/logo-white.png";
    private ForgotPassword forgotPassword = new ForgotPassword();

    @Data
    public static class ForgotPassword {
        private String subject = "Your ybrainy verification code";
        private int codeLength = 6;
        private long codeTtlMinutes = 10;
    }
}
