package tn.esprit.tpfoyer.Dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiReaderContextDTO {
    String sourceType;
    Long courseId;
    Long lessonId;
    String title;
    String text;
    String talkingHeadBaseUrl;
    String speakerWavPath;
    String speakerSource;
}
