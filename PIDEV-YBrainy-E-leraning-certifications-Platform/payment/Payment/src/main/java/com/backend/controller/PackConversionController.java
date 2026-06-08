package com.backend.controller;

import com.backend.dto.packconversion.PackConversionSummaryResponseDTO;
import com.backend.service.PackConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/packs/conversion")
@RequiredArgsConstructor
public class PackConversionController {

    private final PackConversionService packConversionService;

    @GetMapping("/summary")
    public ResponseEntity<PackConversionSummaryResponseDTO> getSummary(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(packConversionService.getSummary(limit));
    }
}
