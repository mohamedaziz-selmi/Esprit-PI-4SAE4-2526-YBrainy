package com.backend.service;

import com.backend.dto.auth.LoginRequest;
import com.backend.dto.auth.LoginResponse;
import com.backend.dto.auth.UserProfileResponse;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class StaticAuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.static-user.email}")
    private String staticEmail;

    @Value("${auth.static-user.password}")
    private String staticPassword;

    @Value("${auth.static-user.name}")
    private String staticName;

    @Value("${auth.static-user.role:ADMIN}")
    private String staticRole;

    private String encodedPassword;

    public StaticAuthService(JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void init() {
        // Encode once at startup so we never compare clear-text password directly.
        this.encodedPassword = passwordEncoder.encode(staticPassword);
    }

    public LoginResponse login(LoginRequest request) {
        if (!staticEmail.equalsIgnoreCase(request.email())
                || !passwordEncoder.matches(request.password(), encodedPassword)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        Map<String, Object> claims = Map.of(
                "email", staticEmail,
                "name", staticName,
                "role", staticRole
        );

        String token = jwtService.generateToken(staticEmail, claims);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                staticEmail,
                staticName,
                staticRole
        );
    }

    public UserProfileResponse extractProfile(String bearerToken) {
        String token = extractTokenFromBearer(bearerToken);
        Claims claims = jwtService.parseClaims(token);

        return new UserProfileResponse(
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("name", String.class),
                claims.get("role", String.class)
        );
    }

    public String resolveEmailFromBearerOrDefault(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return staticEmail;
        }

        try {
            UserProfileResponse profile = extractProfile(bearerToken);
            if (profile != null && profile.email() != null && !profile.email().isBlank()
                    && profile.email().equalsIgnoreCase(staticEmail)) {
                return staticEmail;
            }
        } catch (Exception ignored) {
        }

        return staticEmail;
    }

    private String extractTokenFromBearer(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        return bearerToken.substring(7);
    }
}
