package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.KeycloakAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAdminServiceCreateUserTest {

    @Test
    void createUserUsesLocationHeaderFromCreateResponse() {
        AtomicInteger requestCount = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            requestCount.incrementAndGet();

            if (request.url().getPath().endsWith("/protocol/openid-connect/token")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"access_token\":\"service-token\",\"expires_in\":300}")
                        .build());
            }

            if (request.method() == HttpMethod.POST && request.url().getPath().endsWith("/admin/realms/microservices/users")) {
                return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                        .header(HttpHeaders.LOCATION,
                                "http://localhost:9190/admin/realms/microservices/users/created-user-id")
                        .build());
            }

            return Mono.error(new AssertionError("Unexpected request: " + request.method() + " " + request.url()));
        };

        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setBaseUrl("http://localhost:9190");
        properties.setRealm("microservices");
        properties.getAdmin().setClientId("bb-user-admin");
        properties.getAdmin().setClientSecret("secret");

        KeycloakAdminService service = new KeycloakAdminService(
                WebClient.builder().exchangeFunction(exchangeFunction).build(),
                properties
        );

        String userId = service.createUser("student_01", "student_01@example.com", "Student", "Example", true);

        assertThat(userId).isEqualTo("created-user-id");
        assertThat(requestCount.get()).isEqualTo(2);
    }
}
