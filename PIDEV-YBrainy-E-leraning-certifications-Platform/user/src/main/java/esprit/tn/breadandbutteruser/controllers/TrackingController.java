package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.TrackingBatchRequestDto;
import esprit.tn.breadandbutteruser.services.InteractionTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final InteractionTrackingService trackingService;

    @PostMapping("/events")
    public ResponseEntity<Void> ingest(@Valid @RequestBody TrackingBatchRequestDto batch,
                                       @AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        String email = jwt != null ? jwt.getClaimAsString("email") : null;
        String role = resolvePrimaryRole(jwt);
        trackingService.ingest(batch, subject, email, role);
        return ResponseEntity.ok().build();
    }

    private String resolvePrimaryRole(Jwt jwt) {
        if (jwt == null) {
            return "UNKNOWN";
        }
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List<?> roles) {
                for (String candidate : List.of("ADMIN", "ENTERPRISE_USER", "INSTRUCTOR", "STUDENT")) {
                    boolean present = roles.stream().map(String::valueOf).anyMatch(candidate::equalsIgnoreCase);
                    if (present) {
                        return candidate;
                    }
                }
                if (!roles.isEmpty()) {
                    return String.valueOf(roles.get(0));
                }
            }
        }
        return "UNKNOWN";
    }
}
