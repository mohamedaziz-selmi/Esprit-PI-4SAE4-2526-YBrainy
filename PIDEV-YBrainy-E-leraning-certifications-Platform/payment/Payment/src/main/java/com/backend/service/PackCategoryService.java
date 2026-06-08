package com.backend.service;

import com.backend.dto.packcategory.CreatePackCategoryDTO;
import com.backend.dto.packcategory.PackCategoryResponseDTO;
import com.backend.dto.packcategory.UpdatePackCategoryDTO;
import com.backend.entity.PackCategory;
import com.backend.entity.enums.CategoryStatus;
import com.backend.entity.enums.PackStatus;
import com.backend.exception.BusinessRuleException;
import com.backend.exception.DuplicateResourceException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.mapper.PackCategoryMapper;
import com.backend.repository.PackCategoryRepository;
import com.backend.repository.PackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PackCategoryService {

    private final PackCategoryRepository categoryRepository;
    private final PackRepository packRepository;
    private final PackCategoryMapper categoryMapper;

    /* ─── Admin: Create ─── */
    public PackCategoryResponseDTO create(CreatePackCategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new DuplicateResourceException("Category with name '" + dto.getName() + "' already exists");
        }
        PackCategory entity = categoryMapper.toEntity(dto);
        entity.setStatus(CategoryStatus.ACTIVE);
        PackCategory saved = categoryRepository.save(entity);
        return categoryMapper.toResponseDTO(saved);
    }

    /* ─── Admin: Update ─── */
    public PackCategoryResponseDTO update(Long id, UpdatePackCategoryDTO dto) {
        PackCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        // Check uniqueness if name changed
        categoryRepository.findByNameIgnoreCase(dto.getName())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new DuplicateResourceException("Category with name '" + dto.getName() + "' already exists");
                    }
                });

        categoryMapper.updateEntity(dto, entity);
        PackCategory updated = categoryRepository.save(entity);
        return categoryMapper.toResponseDTO(updated);
    }

    /* ─── Admin: Delete ─── */
    public void delete(Long id) {
        PackCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        // Business rule: Cannot delete Category if it contains ACTIVE Packs
        if (packRepository.existsByCategoryIdAndStatus(id, PackStatus.ACTIVE)) {
            throw new BusinessRuleException("Cannot delete category that contains ACTIVE packs. Archive or remove the packs first.");
        }

        categoryRepository.delete(entity);
    }

    /* ─── Admin: Toggle Status ─── */
    public PackCategoryResponseDTO toggleStatus(Long id) {
        PackCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (entity.getStatus() == CategoryStatus.ACTIVE) {
            entity.setStatus(CategoryStatus.INACTIVE);
        } else {
            entity.setStatus(CategoryStatus.ACTIVE);
        }

        PackCategory updated = categoryRepository.save(entity);
        return categoryMapper.toResponseDTO(updated);
    }

    /* ─── Admin: Get All ─── */
    @Transactional(readOnly = true)
    public List<PackCategoryResponseDTO> getAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /* ─── Admin: Get By Id ─── */
    @Transactional(readOnly = true)
    public PackCategoryResponseDTO getById(Long id) {
        PackCategory entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        return categoryMapper.toResponseDTO(entity);
    }

    /* ─── Frontoffice: Get Active Categories ─── */
    @Transactional(readOnly = true)
    public List<PackCategoryResponseDTO> getActiveCategories() {
        return categoryRepository.findByStatus(CategoryStatus.ACTIVE).stream()
                .map(categoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}

