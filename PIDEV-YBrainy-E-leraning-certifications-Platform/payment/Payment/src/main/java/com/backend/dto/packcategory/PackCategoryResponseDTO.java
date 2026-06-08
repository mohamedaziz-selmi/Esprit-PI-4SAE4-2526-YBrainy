package com.backend.dto.packcategory;

import com.backend.entity.enums.CategoryStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PackCategoryResponseDTO {

    private Long id;
    private String name;
    private String description;
    private String icon;
    private CategoryStatus status;
    private int packCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

