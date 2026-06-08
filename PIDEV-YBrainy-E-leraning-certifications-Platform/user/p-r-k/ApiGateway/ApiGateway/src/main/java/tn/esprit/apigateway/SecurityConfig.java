package tn.esprit.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // Swagger UI (centralized at Gateway)
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        // Infrastructure
                        .pathMatchers("/actuator/**").permitAll()
                        // Auth — public
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/api/users/uploads/**").permitAll()
                        .pathMatchers("/api/users/internal/**").permitAll()
                        // Courses & learning — fully open (authorization via requestingUserId/requestingRole params)
                        .pathMatchers("/api/courses/**").permitAll()
                        .pathMatchers("/api/learning-paths/**").permitAll()
                        .pathMatchers("/api/certificates/**").permitAll()
                        .pathMatchers("/api/quizzes/**").permitAll()
                        .pathMatchers("/api/lessons/**").permitAll()
                        // Instructor routes — reads are open (backend enforces via requestingUserId/requestingRole), writes require role
                        .pathMatchers(HttpMethod.GET,    "/api/instructor/**").permitAll()
                        .pathMatchers(HttpMethod.POST,   "/api/instructor/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        .pathMatchers(HttpMethod.PUT,    "/api/instructor/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        .pathMatchers(HttpMethod.DELETE, "/api/instructor/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        // Admin-only: student management
                        .pathMatchers("/api/students/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        // Enrolled students only: ml, enrollments, notes
                        .pathMatchers("/api/ml/**").permitAll()
                        .pathMatchers("/api/enrollments/**").permitAll()
                        .pathMatchers("/api/notes/**").permitAll()
                        // Payments / Finance / Cart / Packs / Scraper
                        .pathMatchers("/api/payments/**").permitAll()
                        .pathMatchers("/api/finance/**").permitAll()
                        .pathMatchers("/api/cart/**").permitAll()
                        .pathMatchers("/api/packs/**").permitAll()
                        .pathMatchers("/api/admin/**").permitAll()
                        .pathMatchers("/api/scraper/**").permitAll()
                        // Forum — reads are public, writes require authentication
                        .pathMatchers(HttpMethod.GET,    "/api/categories/**").permitAll()
                        .pathMatchers(HttpMethod.GET,    "/api/threads/**").permitAll()
                        .pathMatchers(HttpMethod.GET,    "/api/posts/**").permitAll()
                        .pathMatchers(HttpMethod.GET,    "/api/comments/**").permitAll()
                        .pathMatchers(HttpMethod.POST,   "/api/categories/**").authenticated()
                        .pathMatchers(HttpMethod.PUT,    "/api/categories/**").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/categories/**").authenticated()
                        .pathMatchers(HttpMethod.POST,   "/api/threads/**").authenticated()
                        .pathMatchers(HttpMethod.PUT,    "/api/threads/**").authenticated()
                        .pathMatchers(HttpMethod.PATCH,  "/api/threads/**").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/threads/**").authenticated()
                        .pathMatchers(HttpMethod.POST,   "/api/posts/**").authenticated()
                        .pathMatchers(HttpMethod.PUT,    "/api/posts/**").authenticated()
                        .pathMatchers(HttpMethod.PATCH,  "/api/posts/**").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/posts/**").authenticated()
                        .pathMatchers(HttpMethod.POST,   "/api/comments/**").authenticated()
                        .pathMatchers(HttpMethod.PUT,    "/api/comments/**").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/comments/**").authenticated()
                        .pathMatchers("/api/messages/**").authenticated()
                        .pathMatchers("/api/wishlist/**").authenticated()
                        .pathMatchers("/api/ai/**").permitAll()
                        .pathMatchers("/api/forum-users/**").authenticated()
                        .pathMatchers("/api/dashboard/**").authenticated()
                        .pathMatchers("/api/notifications/**").authenticated()
                        .pathMatchers("/uploads/**").permitAll()
                        .pathMatchers("/ws/**").permitAll()
                        // Partnership / jobs
                        .pathMatchers("/api/partnerships/**").permitAll()
                        .pathMatchers("/api/offers/**").permitAll()
                        .pathMatchers("/api/applications/**").permitAll()
                        .pathMatchers("/api/generate-application").permitAll()
                        // Events
                        .pathMatchers("/Event/**").permitAll()
                        .pathMatchers("/Inscription/**").permitAll()
                        .pathMatchers("/Feedback/**").permitAll()
                        .pathMatchers("/User/**").permitAll()
                        .pathMatchers("/api/recommendations/**").permitAll()
                        .pathMatchers("/api/events/**").permitAll()
                        .pathMatchers("/api/inscriptions/**").permitAll()
                        .pathMatchers("/api/feedbacks/**").permitAll()
                        .pathMatchers("/api/event-users/**").permitAll()
                        // Admin-only: warnings, bans, appeals
                        .pathMatchers("/api/warnings/**").hasAnyRole("ADMIN", "MODERATOR")
                        .pathMatchers("/api/bans/**").hasAnyRole("ADMIN", "MODERATOR")
                        .pathMatchers("/api/ban-appeals/**").permitAll()
                        .pathMatchers("/api/appeals/**").permitAll()
                        .anyExchange().authenticated()
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.SAMEORIGIN))
                )
                .oauth2ResourceServer(oauth -> oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                    .authenticationEntryPoint((exchange, ex) -> Mono.empty())
                )
                .build();
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new java.util.ArrayList<>();

            // realm_access.roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                Object roles = realmAccess.get("roles");
                if (roles instanceof List<?> roleList) {
                    roleList.stream()
                        .filter(r -> r instanceof String)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + ((String) r).toUpperCase()))
                        .forEach(authorities::add);
                }
            }

            // resource_access.<client>.roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.values().stream()
                    .filter(v -> v instanceof Map)
                    .forEach(v -> {
                        Object roles = ((Map<?, ?>) v).get("roles");
                        if (roles instanceof List<?> roleList) {
                            roleList.stream()
                                .filter(r -> r instanceof String)
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + ((String) r).toUpperCase()))
                                .forEach(authorities::add);
                        }
                    });
            }

            return authorities;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.auth.client-id:${KEYCLOAK_AUTH_CLIENT_ID:angular-client}}") String clientId) {
        String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> clientValidator = new JwtClientClaimValidator(clientId);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, clientValidator));

        return jwtDecoder;
    }
}
