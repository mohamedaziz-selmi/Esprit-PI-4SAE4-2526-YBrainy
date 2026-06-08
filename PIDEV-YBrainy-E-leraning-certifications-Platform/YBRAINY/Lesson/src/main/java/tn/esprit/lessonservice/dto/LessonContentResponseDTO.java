package tn.esprit.lessonservice.dto;

import tn.esprit.lessonservice.entities.LessonType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonContentResponseDTO {
    private Long id;
    private LessonType type;
    private String contentUrl;
    private Integer orderIndex;
    private java.time.LocalDateTime createdAt;
}
