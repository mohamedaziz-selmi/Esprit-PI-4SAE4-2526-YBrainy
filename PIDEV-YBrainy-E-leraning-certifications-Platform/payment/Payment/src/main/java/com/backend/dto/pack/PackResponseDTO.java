package com.backend.dto.pack;

import com.backend.entity.enums.PackLevel;
import com.backend.entity.enums.PackStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackResponseDTO {

    private Long id;
    private String title;
    private String description;
    private Double originalPrice;
    private Double salePrice;
    private PackLevel level;
    private Integer durationHours;
    private String certificateName;
    private String image;
    private PackStatus status;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
