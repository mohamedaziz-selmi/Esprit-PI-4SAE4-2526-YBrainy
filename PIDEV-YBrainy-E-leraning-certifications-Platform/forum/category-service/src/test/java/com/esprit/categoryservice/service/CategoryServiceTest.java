package com.esprit.categoryservice.service;

import com.esprit.categoryservice.dto.CategoryRequest;
import com.esprit.categoryservice.dto.CategoryResponse;
import com.esprit.categoryservice.exception.BadRequestException;
import com.esprit.categoryservice.exception.ResourceNotFoundException;
import com.esprit.categoryservice.model.Category;
import com.esprit.categoryservice.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService service;

    private Category sampleCategory(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .description("A test category")
                .iconUrl("http://icons.example.com/tech.png")
                .build();
    }

    private CategoryRequest sampleRequest(String name) {
        CategoryRequest req = new CategoryRequest();
        req.setName(name);
        req.setDescription("A test category");
        req.setIconUrl("http://icons.example.com/tech.png");
        return req;
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() returns all categories as DTOs")
    void getAll_returnsList() {
        when(categoryRepository.findAll())
                .thenReturn(List.of(sampleCategory(1L, "Java"), sampleCategory(2L, "Python")));

        List<CategoryResponse> result = service.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Java");
        assertThat(result.get(1).getName()).isEqualTo("Python");
    }

    @Test
    @DisplayName("getAll() returns empty list when no categories exist")
    void getAll_empty() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        assertThat(service.getAll()).isEmpty();
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when category exists")
    void getById_found() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory(1L, "Java")));

        CategoryResponse result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("getById() throws ResourceNotFoundException when category not found")
    void getById_notFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves and returns new category")
    void create_success() {
        CategoryRequest req = sampleRequest("Docker");
        Category saved = sampleCategory(10L, "Docker");

        when(categoryRepository.existsByName("Docker")).thenReturn(false);
        when(categoryRepository.save(any())).thenReturn(saved);

        CategoryResponse result = service.create(req);

        assertThat(result.getName()).isEqualTo("Docker");
        verify(categoryRepository).save(any());
    }

    @Test
    @DisplayName("create() throws BadRequestException when category name already exists")
    void create_duplicate_throws() {
        CategoryRequest req = sampleRequest("Java");
        when(categoryRepository.existsByName("Java")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() patches non-null fields and saves")
    void update_partialUpdate() {
        Category existing = sampleCategory(5L, "Go");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any())).thenReturn(existing);

        CategoryRequest req = sampleRequest("GoLang");
        req.setDescription("Updated description");
        req.setIconUrl(null);

        service.update(5L, req);

        assertThat(existing.getName()).isEqualTo("GoLang");
        assertThat(existing.getDescription()).isEqualTo("Updated description");
        assertThat(existing.getIconUrl()).isEqualTo("http://icons.example.com/tech.png");
        verify(categoryRepository).save(existing);
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when category not found")
    void update_notFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, sampleRequest("X")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes category when it exists")
    void delete_success() {
        when(categoryRepository.existsById(7L)).thenReturn(true);
        doNothing().when(categoryRepository).deleteById(7L);

        service.delete(7L);

        verify(categoryRepository).deleteById(7L);
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when category not found")
    void delete_notFound_throws() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }
}
