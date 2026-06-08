package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CourseResponseDTO {

    private Long id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private BigDecimal price;
    private Double rating;
    private Integer ratingCount;
    private CourseCategory category;
    private CourseLevel level;
    private Integer approximateDurationMinutes;
    private Boolean isPublished;
    private Boolean offersCertificate;
    private Integer lessonCount;
    private Long instructorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
