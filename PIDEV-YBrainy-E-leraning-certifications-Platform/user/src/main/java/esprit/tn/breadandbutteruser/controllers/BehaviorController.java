package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.BehaviorRequestDto;
import esprit.tn.breadandbutteruser.dto.BehaviorResponseDto;
import esprit.tn.breadandbutteruser.services.BehaviorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BehaviorResponseDto> create(@Valid @RequestBody BehaviorRequestDto request) {
        return new ResponseEntity<>(behaviorService.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isBehaviorOwner(authentication, #id)")
    public ResponseEntity<BehaviorResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(behaviorService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BehaviorResponseDto>> getAll() {
        return ResponseEntity.ok(behaviorService.getAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isBehaviorOwner(authentication, #id)")
    public ResponseEntity<BehaviorResponseDto> update(@PathVariable Long id, @Valid @RequestBody BehaviorRequestDto request) {
        return ResponseEntity.ok(behaviorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        behaviorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
