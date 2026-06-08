package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.PersonalityRequestDto;
import esprit.tn.breadandbutteruser.dto.PersonalityResponseDto;
import esprit.tn.breadandbutteruser.services.PersonalityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/personalities")
@RequiredArgsConstructor
public class PersonalityController {

    private final PersonalityService personalityService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PersonalityResponseDto> create(@Valid @RequestBody PersonalityRequestDto request) {
        return new ResponseEntity<>(personalityService.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isPersonalityOwner(authentication, #id)")
    public ResponseEntity<PersonalityResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(personalityService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PersonalityResponseDto>> getAll() {
        return ResponseEntity.ok(personalityService.getAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isPersonalityOwner(authentication, #id)")
    public ResponseEntity<PersonalityResponseDto> update(@PathVariable Long id, @Valid @RequestBody PersonalityRequestDto request) {
        return ResponseEntity.ok(personalityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personalityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
