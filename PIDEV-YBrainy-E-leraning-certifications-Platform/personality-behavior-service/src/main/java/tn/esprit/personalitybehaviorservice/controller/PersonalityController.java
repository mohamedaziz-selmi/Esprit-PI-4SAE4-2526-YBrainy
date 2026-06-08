package tn.esprit.personalitybehaviorservice.controller;

import tn.esprit.personalitybehaviorservice.dto.PersonalityRequestDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityResponseDto;
import tn.esprit.personalitybehaviorservice.service.PersonalityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/personalities")
@RequiredArgsConstructor
public class PersonalityController {

    private final PersonalityService personalityService;

    @PostMapping
    public ResponseEntity<PersonalityResponseDto> create(@Valid @RequestBody PersonalityRequestDto request) {
        return new ResponseEntity<>(personalityService.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonalityResponseDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(personalityService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PersonalityResponseDto> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(personalityService.getByUserId(userId));
    }

    @GetMapping
    public ResponseEntity<List<PersonalityResponseDto>> getAll() {
        return ResponseEntity.ok(personalityService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonalityResponseDto> update(@PathVariable String id,
            @Valid @RequestBody PersonalityRequestDto request) {
        return ResponseEntity.ok(personalityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        personalityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
