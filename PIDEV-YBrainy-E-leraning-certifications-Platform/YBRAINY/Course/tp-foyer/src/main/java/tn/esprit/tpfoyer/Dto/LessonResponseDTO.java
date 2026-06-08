package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.LessonType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LessonResponseDTO {

    private Long id;
    private String title;
    private String description;
    private LessonType type;
    private String contentUrl;
    private List<LessonContentResponseDTO> contents;
    private Integer orderIndex;
    private Integer durationMinutes;
    private Long courseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
