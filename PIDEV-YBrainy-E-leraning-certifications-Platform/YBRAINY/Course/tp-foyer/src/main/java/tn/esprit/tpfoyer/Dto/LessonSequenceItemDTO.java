package tn.esprit.tpfoyer.Dto;

import lombok.Data;
import tn.esprit.tpfoyer.Entities.enums.LessonType;

@Data
public class LessonSequenceItemDTO {
    private LessonType type;

    // If set, this sequence step references an existing content item (used in update without reupload)
    private Long existingContentId;

    // For uploaded files: index within the corresponding multipart array (videos/pdfs/images)
    private Integer fileIndex;

    // For YouTube: index within the youtubeUrls list
    private Integer youtubeIndex;
}
