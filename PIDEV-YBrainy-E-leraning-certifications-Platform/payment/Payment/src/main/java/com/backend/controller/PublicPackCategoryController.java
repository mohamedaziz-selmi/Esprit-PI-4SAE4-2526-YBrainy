package com.backend.controller;

import com.backend.dto.packcategory.PackCategoryResponseDTO;
import com.backend.service.PackCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class PublicPackCategoryController {

    private final PackCategoryService categoryService;

    @GetMapping("/active")
    public ResponseEntity<List<PackCategoryResponseDTO>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }
}

