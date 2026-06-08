package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.services.KeycloakAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/keycloak")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Keycloak", description = "Admin-only endpoints to manage Keycloak users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final KeycloakAdminService keycloakAdminService;

    @GetMapping("/users")
    @Operation(summary = "List Keycloak users", description = "Optional search query filters by username/email/first/last name")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer first,
            @RequestParam(required = false) Integer max) {
        log.info("Admin request to list Keycloak users with search: {}, first: {}, max: {}", search, first, max);
        List<Map<String, Object>> users = keycloakAdminService.listUsers(search, first, max);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{userId}/enabled")
    @Operation(summary = "Enable or disable a user", description = "Set enabled=true or enabled=false")
    public ResponseEntity<Void> setEnabled(@PathVariable String userId, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Admin request to set user {} enabled={}", userId, enabled);
        keycloakAdminService.setEnabled(userId, enabled);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Change a user's realm role", description = "Provide role: STUDENT|INSTRUCTOR|ENTERPRISE_USER|ADMIN")
    public ResponseEntity<Void> updateRole(@PathVariable String userId, @RequestBody Map<String, String> body) {
        String roleName = body.get("role");
        if (roleName == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Role newRole = Role.fromString(roleName);
            log.info("Admin request to update user {} role to {}", userId, newRole);
            keycloakAdminService.updateRole(userId, newRole);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/users/{userId}/company")
    @Operation(summary = "Set company attribute for a user")
    public ResponseEntity<Void> setCompany(@PathVariable String userId, @RequestBody Map<String, String> body) {
        String company = body.get("company");
        if (company == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Admin request to set user {} company to {}", userId, company);
        keycloakAdminService.setCompany(userId, company);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{userId}/approved")
    @Operation(summary = "Approve or deny a user")
    public ResponseEntity<Void> setApproved(@PathVariable String userId, @RequestBody Map<String, Boolean> body) {
        Boolean approved = body.get("approved");
        if (approved == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Admin request to set user {} approved={}", userId, approved);
        keycloakAdminService.setApproved(userId, approved);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{userId}/banned")
    @Operation(summary = "Ban or unban a user")
    public ResponseEntity<Void> setBanned(@PathVariable String userId, @RequestBody Map<String, Boolean> body) {
        Boolean banned = body.get("banned");
        if (banned == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Admin request to set user {} banned={}", userId, banned);
        keycloakAdminService.setBanned(userId, banned);
        return ResponseEntity.ok().build();
    }
}
