package esprit.tn.breadandbutteruser.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import esprit.tn.breadandbutteruser.config.KeycloakAdminProperties;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final KeycloakAdminProperties keycloakAdminProperties;

    public SecurityConfig(KeycloakAdminProperties keycloakAdminProperties) {
        this.keycloakAdminProperties = keycloakAdminProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/public/**", "/actuator/**", "/images/**").permitAll()
                        .requestMatchers(
                                "/api/auth/signup/challenge",
                                "/api/auth/signup",
                                "/api/auth/signin",
                                "/api/auth/login",
                                "/api/auth/face/signin",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/forgot-password/reset-with-code",
                                "/api/ban-appeals/public",
                                "/api/users/uploads/**",
                                "/api/users/internal",
                                "/api/users/internal/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        String issuerUri = keycloakAdminProperties.getBaseUrl() + "/realms/" + keycloakAdminProperties.getRealm();
        String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> clientValidator =
                new KeycloakClientClaimValidator(keycloakAdminProperties.getAuth().getClientId());
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, clientValidator));

        return jwtDecoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) return List.of();

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());

            // 👇 IMPORTANT FIX: return Collection<GrantedAuthority>
            Collection<GrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

            return authorities;
        });

        return converter;
    }
}
