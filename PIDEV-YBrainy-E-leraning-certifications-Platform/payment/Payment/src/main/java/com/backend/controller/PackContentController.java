package com.backend.controller;

import com.backend.dto.pack.GeneratePackContentRequestDTO;
import com.backend.dto.pack.GeneratePackContentResponseDTO;
import com.backend.service.PackContentGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/packs/content")
@RequiredArgsConstructor
public class PackContentController {

    private final PackContentGenerationService packContentGenerationService;

    @PostMapping("/generate")
    public ResponseEntity<GeneratePackContentResponseDTO> generate(@Valid @RequestBody GeneratePackContentRequestDTO request) {
        return ResponseEntity.ok(packContentGenerationService.generateContent(request));
    }
}
