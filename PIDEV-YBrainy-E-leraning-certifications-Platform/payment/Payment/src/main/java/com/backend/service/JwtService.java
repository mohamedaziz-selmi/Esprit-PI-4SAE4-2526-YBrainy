package com.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    public String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return jwtExpirationMs / 1000;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
