package tn.esprit.tpfoyer.Dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.tpfoyer.Entities.enums.LessonType;

import java.time.LocalDateTime;

@Data
@Builder
public class LessonContentResponseDTO {
    private Long id;
    private LessonType type;
    private String contentUrl;
    private Integer orderIndex;
    private LocalDateTime createdAt;
}
