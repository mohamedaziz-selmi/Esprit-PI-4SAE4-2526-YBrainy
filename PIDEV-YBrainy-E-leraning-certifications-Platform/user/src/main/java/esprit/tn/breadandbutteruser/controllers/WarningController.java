package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.WarningRequestDto;
import esprit.tn.breadandbutteruser.dto.WarningResponseDto;
import esprit.tn.breadandbutteruser.services.AuthorizationHelper;
import esprit.tn.breadandbutteruser.services.WarningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;
    private final AuthorizationHelper authorizationHelper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarningResponseDto> create(@Valid @RequestBody WarningRequestDto request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return new ResponseEntity<>(warningService.create(request, authorizationHelper.actorName(jwt)), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isWarningOwner(authentication, #id)")
    public ResponseEntity<WarningResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(warningService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WarningResponseDto>> getAll() {
        return ResponseEntity.ok(warningService.getAll());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #userId)")
    public ResponseEntity<List<WarningResponseDto>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(warningService.getByUserId(userId));
    }

    @GetMapping("/user/{userId}/count")
    @PreAuthorize("hasRole('ADMIN') or @authz.isSelfUserId(authentication, #userId)")
    public ResponseEntity<Long> countByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(warningService.countByUserId(userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarningResponseDto> update(@PathVariable Long id,
                                                     @Valid @RequestBody WarningRequestDto request) {
        return ResponseEntity.ok(warningService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        warningService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
