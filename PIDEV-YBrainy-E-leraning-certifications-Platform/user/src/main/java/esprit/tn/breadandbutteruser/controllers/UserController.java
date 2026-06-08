package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.AdminUserOverviewDto;
import esprit.tn.breadandbutteruser.dto.InternalUserResponse;
import esprit.tn.breadandbutteruser.dto.UserActivityDto;
import esprit.tn.breadandbutteruser.dto.UserDto;
import esprit.tn.breadandbutteruser.dto.UpdateUserDto;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.services.AuthorizationHelper;
import esprit.tn.breadandbutteruser.services.AvatarStorageService;
import esprit.tn.breadandbutteruser.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    private final UserService userService;
    private final AuthorizationHelper authorizationHelper;
    private final AvatarStorageService avatarStorageService;

    /** Internal service-to-service lookup — no JWT required. Gateway permits this path. */
    @GetMapping("/internal/by-keycloak/{keycloakId}")
    public ResponseEntity<InternalUserResponse> getUserByKeycloakId(@PathVariable String keycloakId) {
        return userService.getInternalUserByKeycloakId(keycloakId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<InternalUserResponse> getUserByIdInternal(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getInternalUser(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/internal")
    public ResponseEntity<List<UserDto>> getAllUsersInternal() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /** Internal service-to-service lookup for all user IDs. */
    @GetMapping("/internal/ids")
    public ResponseEntity<List<Long>> getAllUserIdsInternal() {
        return ResponseEntity.ok(userService.getAllUserIds());
    }

    /** Internal service-to-service lookup for user IDs by role. */
    @GetMapping("/internal/ids-by-role")
    public ResponseEntity<List<Long>> getUserIdsByRoleInternal(@RequestParam String role) {
        return ResponseEntity.ok(userService.getUserIdsByRole(parseRole(role)));
    }

    /** Internal service-to-service lookup for the first user by role. */
    @GetMapping("/internal/first-by-role")
    public ResponseEntity<UserDto> getFirstUserByRoleInternal(@RequestParam String role) {
        return userService.getFirstUserByRole(parseRole(role))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their ID")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<UserDto> getUserById(
            @Parameter(description = "User ID") @PathVariable Long id) {
        log.info("REST request to get user by ID: {}", id);
        UserDto result = userService.getUserById(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get user by username", description = "Retrieves a user by their username")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUsername(authentication, #username)")
    public ResponseEntity<UserDto> getUserByUsername(
            @Parameter(description = "Username") @PathVariable String username) {
        log.info("REST request to get user by username: {}", username);
        UserDto result = userService.getUserByUsername(username);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get user by email", description = "Retrieves a user by their email")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfEmail(authentication, #email)")
    public ResponseEntity<UserDto> getUserByEmail(
            @Parameter(description = "Email") @PathVariable String email) {
        log.info("REST request to get user by email: {}", email);
        UserDto result = userService.getUserByEmail(email);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves a list of all users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        log.info("REST request to get all users");
        List<UserDto> result = userService.getAllUsers();
        return ResponseEntity.ok(result);
    }

    public record AvatarUploadResponse(String url, String fileName, long sizeBytes) {}
    public record ChangePasswordRequest(
            @NotBlank(message = "New password is required")
            String newPassword,
            @NotBlank(message = "Password confirmation is required")
            String confirmPassword
    ) {}

    @PostMapping(value = "/uploads/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar image", description = "Uploads a profile image file and returns a public URL")
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        log.info("REST request to upload avatar image (originalFilename={}, size={})",
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        var stored = avatarStorageService.storeAvatar(file);
        return ResponseEntity.ok(new AvatarUploadResponse(stored.publicUrl(), stored.fileName(), stored.sizeBytes()));
    }

    public record AdminBanRequest(Boolean banned, String reasonForBan, Integer banPeriodDays) {}
    public record AdminVerificationRequest(Boolean verified) {}

    @GetMapping("/admin/overview")
    @Operation(summary = "Admin user overview", description = "List local users with moderation and verification summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdminUserOverview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        log.info("REST request to get admin user overview (search={}, filter={}, page={}, size={})",
                search, filter, page, size);
        if (page == null && size == null) {
            return ResponseEntity.ok(userService.getAdminUserOverview(search, filter));
        }

        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 20;
        return ResponseEntity.ok(userService.getAdminUserOverviewPage(search, filter, resolvedPage, resolvedSize));
    }

    @GetMapping("/{id}/activities")
    @Operation(summary = "Get recent user activities (admin)", description = "Returns privacy-limited recent interaction events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserActivityDto>> getUserActivities(
            @Parameter(description = "User ID") @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("REST request to get recent activities for user ID {} (limit={})", id, limit);
        return ResponseEntity.ok(userService.getRecentActivities(id, limit));
    }

    @PutMapping("/{id}/admin-verification")
    @Operation(summary = "Set admin verification", description = "Verifies or un-verifies instructors and enterprise users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> setAdminVerification(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody AdminVerificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (request == null || request.verified() == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("REST request to set admin verification for user ID {} to {}", id, request.verified());
        return ResponseEntity.ok(userService.setAdminVerification(id, request.verified(), authorizationHelper.actorName(jwt)));
    }

    @PutMapping("/{id}/ban")
    @Operation(summary = "Ban or unban user", description = "Stores ban reason/period locally and syncs enabled state to Keycloak")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> setBan(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody AdminBanRequest request) {
        if (request == null || request.banned() == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("REST request to set ban for user ID {} to {} (periodDays={})", id, request.banned(), request.banPeriodDays());
        return ResponseEntity.ok(userService.setUserBan(id, request.banned(), request.reasonForBan(), request.banPeriodDays()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes a user by their ID")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        log.info("REST request to delete user with ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates a user profile")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<UserDto> updateUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody UpdateUserDto dto) {
        log.info("REST request to update user with ID: {}", id);
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @PostMapping(value = "/{id}/face-biometric", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Register or replace a face biometric", description = "Stores a face enrollment image and syncs the derived biometric hash to the linked Keycloak user.")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<UserDto> registerFaceBiometric(
            @Parameter(description = "User ID") @PathVariable Long id,
            @RequestPart("image") MultipartFile image) {
        log.info("REST request to register face biometric for user ID {}", id);
        return ResponseEntity.ok(userService.registerFaceBiometric(id, image));
    }

    @DeleteMapping("/{id}/face-biometric")
    @Operation(summary = "Remove face biometric", description = "Deletes the enrolled face image and clears the linked biometric hash.")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<UserDto> removeFaceBiometric(
            @Parameter(description = "User ID") @PathVariable Long id) {
        log.info("REST request to remove face biometric for user ID {}", id);
        return ResponseEntity.ok(userService.removeFaceBiometric(id));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Change account password", description = "Changes the user password in Keycloak")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #id)")
    public ResponseEntity<Void> changePassword(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("REST request to change password for user ID {}", id);
        userService.changePassword(id, request.newPassword(), request.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    private Role parseRole(String rawRole) {
        try {
            return Role.fromString(rawRole);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + rawRole);
        }
    }
}
