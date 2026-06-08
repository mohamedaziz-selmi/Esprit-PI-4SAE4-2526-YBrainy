package esprit.tn.breadandbutteruser.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAdminServiceErrorMappingTest {

    @Test
    void mapsInvalidClientToConfigMessage() {
        String message = KeycloakAdminService.mapKeycloakLoginError(
                401,
                "{\"error\":\"invalid_client\",\"error_description\":\"Invalid client or Invalid client credentials\"}"
        );

        assertThat(message).isEqualTo("Keycloak auth client configuration is invalid (client id/secret)");
    }

    @Test
    void mapsUnauthorizedClientToDirectGrantsMessage() {
        String message = KeycloakAdminService.mapKeycloakLoginError(
                400,
                "{\"error\":\"unauthorized_client\",\"error_description\":\"Client not allowed for direct access grants\"}"
        );

        assertThat(message).isEqualTo("Keycloak auth client is not allowed to use password login (enable Direct Access Grants)");
    }

    @Test
    void mapsInvalidGrantToInvalidCredentials() {
        String message = KeycloakAdminService.mapKeycloakLoginError(
                400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}"
        );

        assertThat(message).isEqualTo("Invalid credentials");
    }

    @Test
    void keepsUnexpectedStatusVerbose() {
        String message = KeycloakAdminService.mapKeycloakLoginError(503, "service unavailable");

        assertThat(message).isEqualTo("Keycloak login failed: HTTP 503 Body: service unavailable");
    }
}
