package esprit.tn.breadandbutteruser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakAdminProperties {
    private String baseUrl;
    private String realm;

    private Admin admin = new Admin();
    private Auth auth = new Auth();

    @Data
    public static class Admin {
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;
        private String realm = "master";
        private String fallbackClientId = "admin-cli";
    }

    @Data
    public static class Auth {
        private String clientId;
        private String clientSecret;
    }
}
