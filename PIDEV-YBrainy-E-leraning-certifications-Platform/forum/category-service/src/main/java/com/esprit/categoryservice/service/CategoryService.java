package com.esprit.categoryservice.service;

import com.esprit.categoryservice.dto.CategoryRequest;
import com.esprit.categoryservice.dto.CategoryResponse;
import com.esprit.categoryservice.exception.BadRequestException;
import com.esprit.categoryservice.exception.ResourceNotFoundException;
import com.esprit.categoryservice.model.Category;
import com.esprit.categoryservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public CategoryResponse getById(Long id) {
        return toResponse(categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)));
    }

    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new BadRequestException("Category already exists: " + request.getName());
        }
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getIconUrl() != null) {
            category.setIconUrl(request.getIconUrl());
        }
        return toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .iconUrl(c.getIconUrl())
                .build();
    }
}
