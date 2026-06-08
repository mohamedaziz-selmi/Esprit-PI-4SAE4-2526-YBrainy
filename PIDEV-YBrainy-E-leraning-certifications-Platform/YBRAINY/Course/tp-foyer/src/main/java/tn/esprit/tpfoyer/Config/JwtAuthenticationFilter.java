package tn.esprit.tpfoyer.Config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Authentication Filter that validates JWT tokens from the Authorization header.
 * Extracts user information and roles from the token and sets up Spring Security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            final String jwt = authHeader.substring(7);
            
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            
            String username = claims.getSubject();
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Extract roles from claims
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                
                List<SimpleGrantedAuthority> authorities = roles != null
                        ? roles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .collect(Collectors.toList())
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                
                // Create authentication token
                Authentication authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                );
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // Token validation failed - clear context and continue
            SecurityContextHolder.clearContext();
            logger.debug("JWT validation failed: " + e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}
