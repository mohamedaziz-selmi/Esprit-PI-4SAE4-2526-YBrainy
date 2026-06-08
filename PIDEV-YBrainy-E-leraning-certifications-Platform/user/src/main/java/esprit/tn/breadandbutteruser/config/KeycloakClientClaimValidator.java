package esprit.tn.breadandbutteruser.config;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public class KeycloakClientClaimValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error CLIENT_MISMATCH_ERROR = new OAuth2Error(
            "invalid_token",
            "Token was not issued for the configured Keycloak client",
            null
    );

    private final String expectedClientId;

    public KeycloakClientClaimValidator(String expectedClientId) {
        this.expectedClientId = expectedClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!StringUtils.hasText(expectedClientId)) {
            return OAuth2TokenValidatorResult.success();
        }

        String authorizedParty = token.getClaimAsString("azp");
        if (expectedClientId.equals(authorizedParty)) {
            return OAuth2TokenValidatorResult.success();
        }

        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(expectedClientId)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(CLIENT_MISMATCH_ERROR);
    }
}
