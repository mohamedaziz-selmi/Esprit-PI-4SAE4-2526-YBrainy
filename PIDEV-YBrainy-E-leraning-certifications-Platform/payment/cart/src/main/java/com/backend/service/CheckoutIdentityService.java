package com.backend.service;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CheckoutIdentityService {

    private final JwtService jwtService;

    @Value("${auth.static-user.email}")
    private String fallbackEmail;

    public CheckoutIdentityService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public String resolveEmailFromBearerOrDefault(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank() || !bearerToken.startsWith("Bearer ")) {
            return fallbackEmail;
        }

        try {
            Claims claims = jwtService.parseClaims(bearerToken.substring(7));
            String email = claims.get("email", String.class);
            if (email != null && !email.isBlank()) {
                return email;
            }
        } catch (Exception ignored) {
        }

        return fallbackEmail;
    }
}
