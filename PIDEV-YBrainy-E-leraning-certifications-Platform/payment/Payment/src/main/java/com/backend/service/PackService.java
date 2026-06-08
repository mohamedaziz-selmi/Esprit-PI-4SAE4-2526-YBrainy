package com.backend.service;

import com.backend.dto.pack.CreatePackDTO;
import com.backend.dto.pack.PackResponseDTO;
import com.backend.dto.pack.UpdatePackDTO;
import com.backend.entity.Pack;
import com.backend.entity.PackCategory;
import com.backend.entity.enums.CategoryStatus;
import com.backend.entity.enums.PackLevel;
import com.backend.entity.enums.PackStatus;
import com.backend.exception.BusinessRuleException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.mapper.PackMapper;
import com.backend.repository.PackCategoryRepository;
import com.backend.repository.PackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PackService {

    private final PackRepository packRepository;
    private final PackCategoryRepository categoryRepository;
    private final PackMapper packMapper;
    private final FileStorageService fileStorageService;


    public PackResponseDTO create(CreatePackDTO dto, org.springframework.web.multipart.MultipartFile file) {

        validatePrices(dto.getOriginalPrice(), dto.getSalePrice());

        PackCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Pack entity = packMapper.toEntity(dto);
        entity.setCategory(category);
        entity.setStatus(PackStatus.DRAFT);

        if (file != null && !file.isEmpty()) {
            try {
                String fileName = fileStorageService.save(file, "packs");
                entity.setImage(fileName);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to store image", e);
            }
        }

        Pack saved = packRepository.save(entity);
        return packMapper.toResponseDTO(saved);
    }


    public PackResponseDTO create(CreatePackDTO dto) {
        return create(dto, null);
    }


    public PackResponseDTO update(Long id, UpdatePackDTO dto, org.springframework.web.multipart.MultipartFile file) {
        Pack entity = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pack not found with id: " + id));


        validatePrices(dto.getOriginalPrice(), dto.getSalePrice());

        PackCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        packMapper.updateEntity(dto, entity);
        entity.setCategory(category);

        if (file != null && !file.isEmpty()) {
            try {
                String fileName = fileStorageService.save(file, "packs");
                entity.setImage(fileName);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to store image", e);
            }
        }

        Pack updated = packRepository.save(entity);
        return packMapper.toResponseDTO(updated);
    }

    public PackResponseDTO update(Long id, UpdatePackDTO dto) {
        return update(id, dto, null);
    }


    public void delete(Long id) {
        Pack entity = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pack not found with id: " + id));
        packRepository.delete(entity);
    }


    public PackResponseDTO changeStatus(Long id, PackStatus newStatus) {
        Pack entity = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pack not found with id: " + id));


        if (newStatus == PackStatus.ACTIVE && entity.getCategory().getStatus() == CategoryStatus.INACTIVE) {
            throw new BusinessRuleException(
                    "Cannot activate pack because its category '" + entity.getCategory().getName() + "' is INACTIVE");
        }

        entity.setStatus(newStatus);
        Pack updated = packRepository.save(entity);
        return packMapper.toResponseDTO(updated);
    }


    @Transactional(readOnly = true)
    public PackResponseDTO getById(Long id) {
        Pack entity = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pack not found with id: " + id));
        return packMapper.toResponseDTO(entity);
    }


    @Transactional(readOnly = true)
    public Page<PackResponseDTO> getAllFiltered(Long categoryId, PackLevel level, PackStatus status,
            Pageable pageable) {
        return packRepository.findWithFilters(categoryId, level, status, pageable)
                .map(packMapper::toResponseDTO);
    }


    @Transactional(readOnly = true)
    public List<PackResponseDTO> getActivePacks() {
        return packRepository.findByStatus(PackStatus.ACTIVE).stream()
                .map(packMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<PackResponseDTO> getActivePacksByCategory(Long categoryId) {

        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
        return packRepository.findByCategoryIdAndStatus(categoryId, PackStatus.ACTIVE).stream()
                .map(packMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public PackResponseDTO getActivePackById(Long id) {
        Pack entity = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pack not found with id: " + id));
        if (entity.getStatus() != PackStatus.ACTIVE) {
            throw new ResourceNotFoundException("Pack not found with id: " + id);
        }
        return packMapper.toResponseDTO(entity);
    }


    private void validatePrices(Double originalPrice, Double salePrice) {
        if (salePrice > originalPrice) {
            throw new BusinessRuleException(
                    "Sale price (" + salePrice + ") cannot be greater than original price (" + originalPrice + ")");
        }
    }
}
