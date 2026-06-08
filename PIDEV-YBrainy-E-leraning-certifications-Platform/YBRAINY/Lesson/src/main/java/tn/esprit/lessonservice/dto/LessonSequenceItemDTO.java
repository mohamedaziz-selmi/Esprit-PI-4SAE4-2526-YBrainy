package tn.esprit.lessonservice.dto;

import tn.esprit.lessonservice.entities.LessonType;
import lombok.Data;

@Data
public class LessonSequenceItemDTO {
    private LessonType type;
    private Long existingContentId;
    private Integer fileIndex;
    private Integer youtubeIndex;
}
