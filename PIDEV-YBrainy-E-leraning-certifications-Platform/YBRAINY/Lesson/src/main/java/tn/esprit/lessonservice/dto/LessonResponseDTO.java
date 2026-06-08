package tn.esprit.lessonservice.dto;

import tn.esprit.lessonservice.entities.LessonType;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
