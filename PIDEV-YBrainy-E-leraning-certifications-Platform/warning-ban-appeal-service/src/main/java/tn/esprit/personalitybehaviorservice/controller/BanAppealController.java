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
import tn.esprit.warningbanappealservice.dto.BanAppealRequestDto;
import tn.esprit.warningbanappealservice.dto.BanAppealResponseDto;
import tn.esprit.warningbanappealservice.service.BanAppealService;

import java.util.List;

@RestController
@RequestMapping("/api/ban-appeals")
@RequiredArgsConstructor
public class BanAppealController {

    private final BanAppealService banAppealService;

    @PostMapping
    public BanAppealResponseDto create(@Valid @RequestBody BanAppealRequestDto request) {
        return banAppealService.create(request);
    }

    @GetMapping("/{id}")
    public BanAppealResponseDto getById(@PathVariable String id) {
        return banAppealService.getById(id);
    }

    @GetMapping
    public List<BanAppealResponseDto> getAll(@RequestParam(required = false) Long userId) {
        return userId == null ? banAppealService.getAll() : banAppealService.getByUserId(userId);
    }

    @PutMapping("/{id}")
    public BanAppealResponseDto update(@PathVariable String id, @Valid @RequestBody BanAppealRequestDto request) {
        return banAppealService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        banAppealService.delete(id);
    }
}
