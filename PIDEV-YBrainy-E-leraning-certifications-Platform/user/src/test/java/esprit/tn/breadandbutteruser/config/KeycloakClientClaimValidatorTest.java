package esprit.tn.breadandbutteruser.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakClientClaimValidatorTest {

    @Test
    void acceptsMatchingAuthorizedParty() {
        KeycloakClientClaimValidator validator = new KeycloakClientClaimValidator("angular-client");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "angular-client")
                .claim("iss", "http://localhost:9190/realms/microservices")
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void acceptsMatchingAudience() {
        KeycloakClientClaimValidator validator = new KeycloakClientClaimValidator("angular-client");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("aud", List.of("account", "angular-client"))
                .claim("iss", "http://localhost:9190/realms/microservices")
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensForOtherClients() {
        KeycloakClientClaimValidator validator = new KeycloakClientClaimValidator("angular-client");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "other-client")
                .claim("aud", List.of("account"))
                .claim("iss", "http://localhost:9190/realms/microservices")
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
