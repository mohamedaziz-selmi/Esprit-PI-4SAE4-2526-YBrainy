package tn.esprit.personalitybehaviorservice.controller;

import tn.esprit.personalitybehaviorservice.dto.BehaviorRequestDto;
import tn.esprit.personalitybehaviorservice.dto.BehaviorResponseDto;
import tn.esprit.personalitybehaviorservice.service.BehaviorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    @PostMapping
    public ResponseEntity<BehaviorResponseDto> create(@Valid @RequestBody BehaviorRequestDto request) {
        return new ResponseEntity<>(behaviorService.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BehaviorResponseDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(behaviorService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<BehaviorResponseDto> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(behaviorService.getByUserId(userId));
    }

    @GetMapping
    public ResponseEntity<List<BehaviorResponseDto>> getAll() {
        return ResponseEntity.ok(behaviorService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BehaviorResponseDto> update(@PathVariable String id,
            @Valid @RequestBody BehaviorRequestDto request) {
        return ResponseEntity.ok(behaviorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        behaviorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
