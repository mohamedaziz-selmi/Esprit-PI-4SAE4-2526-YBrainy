package com.backend.controller;

import com.backend.dto.pack.CreatePackDTO;
import com.backend.dto.pack.PackResponseDTO;
import com.backend.dto.pack.UpdatePackDTO;
import com.backend.entity.enums.PackLevel;
import com.backend.entity.enums.PackStatus;
import com.backend.service.PackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/packs")
@RequiredArgsConstructor
public class AdminPackController {

    private final PackService packService;

    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<PackResponseDTO> create(
            @RequestPart("pack") @Valid CreatePackDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return new ResponseEntity<>(packService.create(dto, file), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = { "multipart/form-data" })
    public ResponseEntity<PackResponseDTO> update(
            @PathVariable Long id,
            @RequestPart("pack") @Valid UpdatePackDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(packService.update(id, dto, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        packService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PackResponseDTO> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        PackStatus status = PackStatus.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(packService.changeStatus(id, status));
    }

    @GetMapping
    public ResponseEntity<Page<PackResponseDTO>> getAll(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PackLevel level,
            @RequestParam(required = false) PackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(packService.getAllFiltered(categoryId, level, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(packService.getById(id));
    }
}
