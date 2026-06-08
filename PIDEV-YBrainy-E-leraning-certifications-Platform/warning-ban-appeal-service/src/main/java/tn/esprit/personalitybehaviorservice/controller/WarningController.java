package tn.esprit.warningbanappealservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.warningbanappealservice.dto.WarningRequestDto;
import tn.esprit.warningbanappealservice.dto.WarningResponseDto;
import tn.esprit.warningbanappealservice.service.WarningService;

import java.util.List;

@RestController
@RequestMapping("/api/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;

    @PostMapping
    public WarningResponseDto create(@Valid @RequestBody WarningRequestDto request) {
        return warningService.create(request);
    }

    @GetMapping("/{id}")
    public WarningResponseDto getById(@PathVariable String id) {
        return warningService.getById(id);
    }

    @GetMapping
    public List<WarningResponseDto> getAll(@RequestParam(required = false) Long userId) {
        return userId == null ? warningService.getAll() : warningService.getByUserId(userId);
    }

    @PutMapping("/{id}")
    public WarningResponseDto update(@PathVariable String id, @Valid @RequestBody WarningRequestDto request) {
        return warningService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        warningService.delete(id);
    }
}
